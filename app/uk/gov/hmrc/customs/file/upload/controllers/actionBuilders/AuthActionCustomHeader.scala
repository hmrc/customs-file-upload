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

package uk.gov.hmrc.customs.file.upload.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.file.upload.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.{HasConversationId, HasRequest}
import uk.gov.hmrc.customs.file.upload.model.{AuthorisedAsCsp, CspWithEori, Eori}
import uk.gov.hmrc.customs.file.upload.services.{CustomsAuthService, FileUploadConfigService}

import scala.concurrent.ExecutionContext

abstract class AuthActionCustomHeader @Inject()(customsAuthService: CustomsAuthService,
                                                headerValidator: HeaderValidator,
                                                logger: FileUploadLogger,
                                                fileUploadConfigService: FileUploadConfigService,
                                                eoriHeaderName: String)
                                               (implicit ec: ExecutionContext)
  extends AuthAction(customsAuthService, headerValidator, logger, fileUploadConfigService) {

  override def eitherCspAuthData[A]()(implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, AuthorisedAsCsp] = {
    for {
      badgeId <- eitherBadgeIdentifier.right
      eori <- eitherEori.right
    } yield CspWithEori(badgeId, eori)
  }

  private def eitherEori[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Eori] = {
    headerValidator.eoriMustBeValidAndPresent(eoriHeaderName)
  }

}

@Singleton
class AuthActionEoriHeader @Inject()(customsAuthService: CustomsAuthService,
                                     headerValidator: HeaderValidator,
                                     logger: FileUploadLogger,
                                     fileUploadConfigService: FileUploadConfigService)
                                    (implicit ec: ExecutionContext)
  extends AuthActionCustomHeader(customsAuthService, headerValidator, logger, fileUploadConfigService, XEoriIdentifierHeaderName) {
  override def requestRetrievalsForEndpoint: Boolean = false
}
