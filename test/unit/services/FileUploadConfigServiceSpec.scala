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

package unit.services

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatestplus.mockito.MockitoSugar
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.customs.api.common.config.{ConfigValidatedNelAdaptor, ServicesConfig}
import uk.gov.hmrc.customs.file.upload.logging.FileUploadLogger
import uk.gov.hmrc.customs.file.upload.services.FileUploadConfigService
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier

class FileUploadConfigServiceSpec extends UnitSpec with MockitoSugar {
  private val validAppConfig: Config = ConfigFactory.parseString(
    """
      |microservice.services.api-subscription-fields.host=some-host
      |microservice.services.api-subscription-fields.port=1111
      |microservice.services.api-subscription-fields.context=/some-context
      |microservice.services.customs-notification.host=some-host2
      |microservice.services.customs-notification.port=1112
      |microservice.services.customs-notification.bearer-token=some-token
      |microservice.services.customs-notification.context=/some-context2
      |upscan-callback.url="http://upscan-callback.url"
      |file-upload-upscan-callback.url="http://file-upload-upscan-callback.url"
      |file-transmission-callback.url="http://some-host3:1113/file-transmission"
      |fileUpload.fileGroupSize.maximum=10
      |fileUpload.fileSize.maximum=100
      |microservice.services.upscan-initiate.host="upscan-initiate.url"
      |microservice.services.upscan-initiate.port=11115
      |microservice.services.upscan-initiate.context=/upscan/initiate
      |microservice.services.file-transmission.host=some-host3
      |microservice.services.file-transmission.port=1113
      |microservice.services.file-transmission.context=/file-transmission
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private val validServicesConfiguration = Configuration(validAppConfig)
  private val emptyServicesConfiguration = Configuration(emptyAppConfig)

  private val mockLogger = mock[FileUploadLogger]

  private def customsConfigService(conf: Configuration) =
    new FileUploadConfigService(new ConfigValidatedNelAdaptor(testServicesConfig(conf), conf), mockLogger)

  "CustomsConfigService" should {
    "return config as object model when configuration is valid" in {
      val configService = customsConfigService(validServicesConfiguration)

      configService.fileUploadConfig.apiSubscriptionFieldsBaseUrl shouldBe "http://some-host:1111/some-context"
      configService.fileUploadConfig.customsNotificationBaseUrl shouldBe "http://some-host2:1112/some-context2"
      configService.fileUploadConfig.customsNotificationBearerToken shouldBe "some-token"
      configService.fileUploadConfig.fileTransmissionCallbackUrl shouldBe "http://some-host3:1113/file-transmission"
      configService.fileUploadConfig.fileUploadCallbackUrl shouldBe "http://file-upload-upscan-callback.url"
      configService.fileUploadConfig.upscanInitiateUrl shouldBe "http://upscan-initiate.url:11115/upscan/initiate"
      configService.fileUploadConfig.upscanInitiateMaximumFileSize shouldBe 100

    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expectedErrorMessage =
        """
          |Could not find config api-subscription-fields.host
          |Service configuration not found for key: api-subscription-fields.context
          |Could not find config customs-notification.host
          |Service configuration not found for key: customs-notification.context
          |Service configuration not found for key: customs-notification.bearer-token
          |Could not find config upscan-initiate.host
          |Service configuration not found for key: upscan-initiate.context
          |Could not find config key 'upscan-callback.url'
          |Could not find config key 'fileUpload.fileSize.maximum'
          |Could not find config key 'file-upload-upscan-callback.url'
          |Could not find config key 'fileUpload.fileGroupSize.maximum'
          |Could not find config key 'file-transmission-callback.url'
          |Could not find config file-transmission.host
          |Service configuration not found for key: file-transmission.context""".stripMargin

      val caught = intercept[IllegalStateException](customsConfigService(emptyServicesConfiguration))
      caught.getMessage shouldBe expectedErrorMessage

      PassByNameVerifier(mockLogger, "errorWithoutRequestContext")
        .withByNameParam[String](expectedErrorMessage)
        .verify()
    }
  }

  private def testServicesConfig(configuration: Configuration) = new ServicesConfig(configuration, mock[Environment]) {
    override val mode: Mode.Value = play.api.Mode.Test
  }

}