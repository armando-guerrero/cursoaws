package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import open_mateo_sdk.WeatherClient;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	layers = {"weather-layer"},
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	runtime = DeploymentRuntime.JAVA11,
	architecture = Architecture.ARM64
)
@LambdaLayer(
		layerName = "weather-layer",
		libraries = {"lib/Open-Meteo-1.0-SNAPSHOT.jar"},
		runtime = DeploymentRuntime.JAVA11,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Object, String> {

	@Override
	public String handleRequest(Object input, Context context) {
		WeatherClient weatherClient = new WeatherClient();
		try {
			return weatherClient.getWeatherForecast();
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return "Error: " + e.getMessage();
		}
	}
}
