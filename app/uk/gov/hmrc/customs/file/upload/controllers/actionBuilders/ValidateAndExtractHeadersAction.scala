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

package uk.gov.hmrc.customs.file.upload.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionRefiner, _}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{ConversationIdRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.http.HttpErrorFunctions

import scala.concurrent.{ExecutionContext, Future}

/** Action builder that validates headers.
  * <ol>
  * <li/>Input - `ConversationIdRequest`
  * <li/>Output - `ValidatedHeadersRequest`
  * <li/>Error - 4XX Result if there is a header validation error. This terminates the action builder pipeline.
  * </ol>
  */
@Singleton
class ValidateAndExtractHeadersAction @Inject()(validator: HeaderValidator,
                                                logger: FileUploadLogger)
                                               (implicit ec: ExecutionContext)
  extends ActionRefiner[ConversationIdRequest, ValidatedHeadersRequest] with HttpErrorFunctions {
    actionName =>

    override def refine[A](cr: ConversationIdRequest[A]): Future[Either[Result, ValidatedHeadersRequest[A]]] = Future.successful {
      implicit val id: ConversationIdRequest[A] = cr

      validator.validateHeaders(cr) match {
        case Left(result) =>
          Left(result.XmlResult.withConversationId)
        case Right(extracted) =>
          val vhr = ValidatedHeadersRequest(cr.conversationId, extracted.requestedApiVersion, extracted.clientId, cr.request)
          Right(vhr)
      }
    }

  override protected def executionContext: ExecutionContext = ec
}
