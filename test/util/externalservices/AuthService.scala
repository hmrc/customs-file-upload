/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util.externalservices

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.customs.file.upload.model.Eori
import util.TestData

trait AuthService {

  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)

  private val customsEnrolmentName = "HMRC-CUS-ORG"

  private val cspAuthorisationPredicate = Enrolment("write:customs-file-upload") and AuthProviders(PrivilegedApplication)
  private val nonCspAuthorisationPredicate = Enrolment(customsEnrolmentName) and AuthProviders(GovernmentGateway)

  private def bearerTokenMatcher(bearerToken: String)= equalTo("Bearer " + bearerToken)

  private def authRequestJson(predicate: Predicate): String = {
    val predicateJsArray = predicateToJson(predicate)
    val js =
      s"""
         |{
         |  "authorise": $predicateJsArray,
         |  "retrieve" : [ ]
         |}
    """.stripMargin
    js
  }

  private def authRequestJsonWithAuthorisedEnrolmentRetrievals(predicate: Predicate) = {
    val predicateJsArray: JsArray = predicateToJson(predicate)
    val js =
      s"""
         |{
         |  "authorise": $predicateJsArray,
         |  "retrieve" : ["authorisedEnrolments"]
         |}
    """.stripMargin
    js
  }

  private def predicateToJson(predicate: Predicate) = {
    predicate.toJson match {
      case arr: JsArray => arr
      case other => Json.arr(other)
    }
  }

  def authServiceUnauthorisesScopeForCSP(bearerToken: String = TestData.cspBearerToken): Unit = {
    cspAuthServiceUnauthorisesScope(bearerToken, authRequestJson(cspAuthorisationPredicate))
  }

  private def cspAuthServiceUnauthorisesScope(bearerToken: String, body: String): Unit = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader(AUTHORIZATION, bearerTokenMatcher(bearerToken))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)
          .withHeader(WWW_AUTHENTICATE, """MDTP detail="InsufficientEnrolments"""")
      )
    )
  }

  def authServiceAuthorizesNonCspWithEori(bearerToken: String = TestData.nonCspBearerToken,
                                          eori: Eori = TestData.declarantEori): Unit = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(authRequestJsonWithAuthorisedEnrolmentRetrievals(nonCspAuthorisationPredicate)))
      .withHeader(AUTHORIZATION, bearerTokenMatcher(bearerToken))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(
            s"""{
               |  "authorisedEnrolments": [ ${enrolmentRetrievalJson(customsEnrolmentName, "EORINumber", eori.value)} ]
               |}""".stripMargin
          )
      )
    )
  }

  def authServiceUnauthorisesCustomsEnrolmentForNonCSP(bearerToken: String = TestData.nonCspBearerToken): Unit = {
    nonCSPAuthServiceUnauthorisesCustomsEnrolment(bearerToken, authRequestJsonWithAuthorisedEnrolmentRetrievals(nonCspAuthorisationPredicate))
  }

  private def nonCSPAuthServiceUnauthorisesCustomsEnrolment(bearerToken: String, body: String): Unit = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader(AUTHORIZATION, bearerTokenMatcher(bearerToken))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)
          .withHeader(WWW_AUTHENTICATE, """MDTP detail="InsufficientEnrolments"""")
      )
    )
  }

  def verifyAuthServiceCalledForCsp(bearerToken: String = TestData.cspBearerToken): Unit = {
    verifyCspAuthServiceCalled(bearerToken, authRequestJson(cspAuthorisationPredicate))
  }

  private def verifyCspAuthServiceCalled(bearerToken: String, body: String): Unit = {
    verify(1, postRequestedFor(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader(AUTHORIZATION, bearerTokenMatcher(bearerToken))
    )
  }

  private def enrolmentRetrievalJson(enrolmentKey: String,
                                     identifierName: String,
                                     identifierValue: String): String = {
    s"""
       |{
       | "key": "$enrolmentKey",
       | "identifiers": [
       |   {
       |     "key": "$identifierName",
       |     "value": "$identifierValue"
       |   }
       | ]
       |}
    """.stripMargin
  }

}
