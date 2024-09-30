package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.*;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import open_mateo_sdk.WeatherClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "processor",
		roleName = "processor-role",
		layers = {"weather-layer"},
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		runtime = DeploymentRuntime.JAVA17,
		architecture = Architecture.ARM64,
		tracingMode = TracingMode.Active
)
@LambdaLayer(
		layerName = "weather-layer",
		libraries = {"lib/Open-Meteo-1.0-SNAPSHOT.jar"},
		runtime = DeploymentRuntime.JAVA17,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "Weather")}
)
public class Processor implements RequestHandler<Object, String> {

	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table table = dynamoDB.getTable(System.getenv("target_table"));
	private final WeatherClient weatherClient = new WeatherClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String handleRequest(Object input, Context context) {
		try {
			String weatherData = weatherClient.getWeatherForecast();
			Map<String, Object> weatherMap = objectMapper.readValue(weatherData, HashMap.class);

			Map<String, Object> hourly = (Map<String, Object>) weatherMap.get("hourly");
			hourly.remove("relative_humidity_2m");
			hourly.remove("wind_speed_10m");

			Map<String, Object> hourlyUnits = (Map<String, Object>) weatherMap.get("hourly");
			hourlyUnits.remove("relative_humidity_2m");
			hourlyUnits.remove("wind_speed_10m");


			Map<String, Object> forecast = new HashMap<>();
			forecast.put("elevation", weatherMap.get("elevation"));
			forecast.put("generationtime_ms", weatherMap.get("generationtime_ms"));
			forecast.put("hourly",hourly);
			forecast.put("hourly_units", hourlyUnits);
			forecast.put("latitude", weatherMap.get("latitude"));
			forecast.put("longitude", weatherMap.get("longitude"));
			forecast.put("timezone", weatherMap.get("timezone"));
			forecast.put("timezone_abbreviation", weatherMap.get("timezone_abbreviation"));
			forecast.put("utc_offset_seconds", weatherMap.get("utc_offset_seconds"));

			forecast.forEach((key, value) -> System.out.println("[Key] : " + key + " [Value] : " + value));

			Item item = new Item()
					.withPrimaryKey("id", UUID.randomUUID().toString())
					.withMap("forecast", forecast);

			table.putItem(item);
			System.out.println("Weather data stored successfully.");
			return "Weather data stored successfully.";
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return "Error: " + e.getMessage();
		}
	}
}