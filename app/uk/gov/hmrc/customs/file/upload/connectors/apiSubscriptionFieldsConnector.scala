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

package uk.gov.hmrc.customs.file.upload.connectors

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.HasConversationId
import uk.gov.hmrc.customs.file.upload.model.{ApiSubscriptionFieldsResponse, ApiSubscriptionKey}
import uk.gov.hmrc.customs.file.upload.services.FileUploadConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ApiSubscriptionFieldsConnector @Inject()(http: HttpClient,
                                               logger: FileUploadLogger,
                                               config: FileUploadConfigService)
                                              (implicit ec: ExecutionContext) {

  def getSubscriptionFields[A](apiSubsKey: ApiSubscriptionKey)(implicit hci: HasConversationId, hc: HeaderCarrier): Future[ApiSubscriptionFieldsResponse] = {
    val url = ApiSubscriptionFieldsPath.url(config.fileUploadConfig.apiSubscriptionFieldsBaseUrl, apiSubsKey)
    get(url)
  }

  private def get[A](url: String)(implicit hci: HasConversationId, hc: HeaderCarrier): Future[ApiSubscriptionFieldsResponse] = {
    logger.debug(s"Getting fields id from api subscription fields service. url=$url")

    http.GET[ApiSubscriptionFieldsResponse](url)
      .recoverWith {
        case httpError: HttpException =>
          logger.error(s"Subscriptions fields lookup call failed. url=$url HttpStatus=${httpError.responseCode} error=${httpError.getMessage}")
          Future.failed(new RuntimeException(httpError))
      }
      .recoverWith {
        case e: Throwable =>
          val msg = s"Call to subscription information service failed. url=$url"
          logger.debug(msg, e)
          logger.error(msg)
          Future.failed(e)
      }
  }

}

object ApiSubscriptionFieldsPath {
  def url(baseUrlAndContext: String, apiSubscriptionKey: ApiSubscriptionKey): String =
    s"$baseUrlAndContext/application/${apiSubscriptionKey.clientId}/context/${apiSubscriptionKey.context}/version/${apiSubscriptionKey.version}"
}