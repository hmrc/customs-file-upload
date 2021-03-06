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

package unit.controllers

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.api.common.controllers.{ErrorResponse, ResponseContents}
import uk.gov.hmrc.customs.file.upload.controllers.actionBuilders.PayloadValidationAction
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.Csp
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{ConversationIdRequest, ValidatedPayloadRequest}
import uk.gov.hmrc.customs.file.upload.services.XmlValidationService
import util.TestData.{conversationId, _}
import util.UnitSpec

import scala.concurrent.Future
import scala.xml.SAXException


class PayloadValidationActionSpec extends UnitSpec with MockitoSugar {

  private implicit val ec = Helpers.stubControllerComponents().executionContext
  private implicit val forConversions: ConversationIdRequest[AnyContentAsXml] = TestConversationIdRequest
  private val saxException = new SAXException("Boom!")
  private val expectedXmlSchemaErrorResult = ErrorResponse
    .errorBadRequest("Payload is not valid according to schema")
    .withErrors(ResponseContents("xml_validation_error", saxException.getMessage)).XmlResult.withConversationId

  trait SetUp {
    val mockXmlValidationService: XmlValidationService = mock[XmlValidationService]
    val mockExportsLogger: FileUploadLogger = mock[FileUploadLogger]
    val payloadValidationAction: PayloadValidationAction = new PayloadValidationAction(mockXmlValidationService, mockExportsLogger){}
  }
  "PayloadValidationAction" should {
    "return a ValidatedPayloadRequest when XML validation is OK" in new SetUp {
      when(mockXmlValidationService.validate(TestCspValidatedPayloadRequest.body.asXml.get)).thenReturn(Future.successful(()))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Right(TestCspValidatedPayloadRequest)
    }

    "return 400 error response when XML is not well formed  (and send GA request)" in new SetUp {
      when(mockXmlValidationService.validate(TestCspValidatedPayloadRequest.body.asXml.get)).thenReturn(Future.failed(saxException))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Left(expectedXmlSchemaErrorResult)
    }

    "return 400 error response when XML validation fails (and send GA request)" in new SetUp {
      private val errorMessage = "Request body does not contain a well-formed XML document."
      private val errorNotWellFormed = ErrorResponse.errorBadRequest(errorMessage).XmlResult.withConversationId
      private val authorisedRequestWithNonWellFormedXml = ConversationIdRequest(conversationId, FakeRequest().withTextBody("<foo><foo>"))
        .toValidatedHeadersRequest(TestExtractedHeaders).toCspAuthorisedRequest(Csp(badgeIdentifier))

      private val actual = await(payloadValidationAction.refine(authorisedRequestWithNonWellFormedXml))

      actual shouldBe Left(errorNotWellFormed)
    }

    "propagates downstream errors by returning a 500 error response (and doesn't send GA request)" in new SetUp {
      when(mockXmlValidationService.validate(TestCspValidatedPayloadRequest.body.asXml.get)).thenReturn(Future.failed(emulatedServiceFailure))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }
  }

}
