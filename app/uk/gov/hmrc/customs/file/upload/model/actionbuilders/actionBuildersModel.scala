/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.customs.file.upload.model.actionbuilders

import play.api.mvc.{Request, Result, WrappedRequest}
import uk.gov.hmrc.customs.file.upload.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.file.upload.model._

import scala.xml.NodeSeq

object ActionBuilderModelHelper {

  implicit class AddConversationId(val result: Result) extends AnyVal {
    def withConversationId(implicit c: HasConversationId): Result = {
      result.withHeaders(XConversationIdHeaderName -> c.conversationId.toString)
    }
  }

  implicit class CorrelationIdsRequestOps[A](val cir: ConversationIdRequest[A]) extends AnyVal {
    def toValidatedHeadersRequest(eh: ExtractedHeaders): ValidatedHeadersRequest[A] =
      ValidatedHeadersRequest(cir.conversationId, eh.requestedApiVersion, eh.clientId, cir.request)
  }

  implicit class ValidatedHeadersRequestOps[A](val vhr: ValidatedHeadersRequest[A]) {

    def toCspAuthorisedRequest(a: AuthorisedAsCsp): AuthorisedRequest[A] = toAuthorisedRequest(a)

    def toNonCspAuthorisedRequest(eori: Eori): AuthorisedRequest[A] = toAuthorisedRequest(NonCsp(eori))

    def toAuthorisedRequest(authorisedAs: AuthorisedAs): AuthorisedRequest[A] =
      AuthorisedRequest(vhr.conversationId, vhr.requestedApiVersion, vhr.clientId, authorisedAs, vhr.request)
  }

  implicit class AuthorisedRequestOps[A](val ar: AuthorisedRequest[A]) extends AnyVal {
    def toValidatedPayloadRequest(xmlBody: NodeSeq): ValidatedPayloadRequest[A] = ValidatedPayloadRequest(
      ar.conversationId,
      ar.requestedApiVersion,
      ar.clientId,
      ar.authorisedAs,
      xmlBody,
      ar.request
    )
  }

  implicit class ValidatedPayloadRequestOps[A](val vpr: ValidatedPayloadRequest[A]) extends AnyVal {

    def toValidatedFileUploadPayloadRequest(fileUploadRequest: FileUploadRequest): ValidatedFileUploadPayloadRequest[A] =
      ValidatedFileUploadPayloadRequest(vpr.conversationId, vpr.requestedApiVersion, vpr.clientId, vpr.authorisedAs, vpr.xmlBody, vpr.request, fileUploadRequest)
  }

}

trait HasRequest[A] {
  val request: Request[A]
}

trait HasConversationId {
  val conversationId: ConversationId
}

trait ExtractedHeaders {
  val requestedApiVersion: ApiVersion
  val clientId: ClientId
}

trait HasAuthorisedAs {
  val authorisedAs: AuthorisedAs
}

trait HasXmlBody {
  val xmlBody: NodeSeq
}

case class FileUploadRequest(declarationId: DeclarationId, fileGroupSize: FileGroupSize, files: Seq[FileUploadFile])

case class FileUploadFile(fileSequenceNo: FileSequenceNo, maybeDocumentType: Option[DocumentType]) {

  def canEqual(a: Any): Boolean = a.isInstanceOf[FileUploadFile]

  override def equals(that: Any): Boolean =
    that match {
      case that: FileUploadFile => that.canEqual(this) && this.hashCode == that.hashCode
      case _ => false
    }
  override def hashCode: Int = {
    fileSequenceNo.value
  }
}

trait HasFileUploadProperties {
  val fileUploadRequest: FileUploadRequest
}

trait HasBadgeIdentifier {
  val badgeIdentifier: BadgeIdentifier
}

case class ExtractedHeadersImpl(
                                 requestedApiVersion: ApiVersion,
                                 clientId: ClientId
                               ) extends ExtractedHeaders

/*
 * We need multiple WrappedRequest classes to reflect additions to context during the request processing pipeline.
 *
 * There is some repetition in the WrappedRequest classes, but the benefit is we get a flat structure for our data
 * items, reducing the number of case classes and making their use much more convenient, rather than deeply nested stuff
 * eg `r.badgeIdentifier` vs `r.requestData.badgeIdentifier`
 */

case class ConversationIdRequest[A](conversationId: ConversationId, request: Request[A]) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId

// Available after ValidatedHeadersAction builder
case class ValidatedHeadersRequest[A](conversationId: ConversationId, requestedApiVersion: ApiVersion, clientId: ClientId, request: Request[A]) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId with ExtractedHeaders

// Available after Authorise action builder
case class AuthorisedRequest[A](conversationId: ConversationId, requestedApiVersion: ApiVersion, clientId: ClientId, authorisedAs: AuthorisedAs, request: Request[A]) extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders with HasAuthorisedAs

// Available after ValidatedPayloadAction builder
abstract class GenericValidatedPayloadRequest[A](
                                                  conversationId: ConversationId,
                                                  requestedApiVersion: ApiVersion,
                                                  clientId: ClientId,
                                                  authorisedAs: AuthorisedAs,
                                                  xmlBody: NodeSeq,
                                                  request: Request[A]
                                                )
  extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders with HasAuthorisedAs with HasXmlBody

case class ValidatedPayloadRequest[A](
                                       conversationId: ConversationId,
                                       requestedApiVersion: ApiVersion,
                                       clientId: ClientId,
                                       authorisedAs: AuthorisedAs,
                                       xmlBody: NodeSeq,
                                       request: Request[A]
                                     )
  extends GenericValidatedPayloadRequest(conversationId, requestedApiVersion, clientId, authorisedAs, xmlBody, request)

case class ValidatedFileUploadPayloadRequest[A](
                                                 conversationId: ConversationId,
                                                 requestedApiVersion: ApiVersion,
                                                 clientId: ClientId,
                                                 authorisedAs: AuthorisedAs,
                                                 xmlBody: NodeSeq,
                                                 request: Request[A],
                                                 fileUploadRequest: FileUploadRequest)
  extends GenericValidatedPayloadRequest(conversationId, requestedApiVersion, clientId, authorisedAs, xmlBody, request) with HasFileUploadProperties
