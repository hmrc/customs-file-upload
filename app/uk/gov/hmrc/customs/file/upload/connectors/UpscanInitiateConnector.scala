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

package uk.gov.hmrc.customs.file.upload.connectors

import com.google.inject._
import play.api.libs.json.Json
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.model.actionbuilders.ValidatedFileUploadPayloadRequest
import uk.gov.hmrc.customs.file.upload.model.{ApiVersion, UpscanInitiatePayload, UpscanInitiateResponsePayload}
import uk.gov.hmrc.customs.file.upload.services.FileUploadConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanInitiateConnector @Inject()(http: HttpClient,
                                        logger: FileUploadLogger,
                                        config: FileUploadConfigService)
                                       (implicit ec: ExecutionContext) {

  def send[A](payload: UpscanInitiatePayload, apiVersion: ApiVersion)(implicit vfupr: ValidatedFileUploadPayloadRequest[A]): Future[UpscanInitiateResponsePayload] = {
    if (payload.isV2) {
      post(payload, config.fileUploadConfig.upscanInitiateV2Url)
    } else {
      post(payload, config.fileUploadConfig.upscanInitiateV1Url)
    }
  }

  private def post[A](payload: UpscanInitiatePayload, url: String)(implicit vfupr: ValidatedFileUploadPayloadRequest[A]) = {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    logger.debug(s"Sending request to upscan initiate service. Url: $url Payload: ${Json.prettyPrint(Json.toJson(payload))}")
    http.POST[UpscanInitiatePayload, UpscanInitiateResponsePayload](url, payload)
      .map { res: UpscanInitiateResponsePayload =>
        logger.info(s"reference from call to upscan initiate ${res.reference}")
        logger.debug(s"Response received from upscan initiate service $res")
        res
      }
      .recoverWith {
        case httpError: HttpException => Future.failed(new RuntimeException(httpError))
        case e: Throwable =>
          logger.error(s"Call to upscan initiate failed. url=$url")
          Future.failed(e)
      }
  }
}
