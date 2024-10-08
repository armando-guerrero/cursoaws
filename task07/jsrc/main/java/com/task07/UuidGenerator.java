package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "uuid_generator",
		roleName = "uuid_generator-role",
		isPublishVersion = false,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(
		targetRule = "uuid_trigger"
)

@DependsOn(
		name = "uuid_trigger",
		resourceType = ResourceType.CLOUDWATCH_RULE
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_bucket", value = "uuid-storage")}
)
public class UuidGenerator implements RequestHandler<Object, String> {

	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String bucketName = System.getenv("target_bucket");

	@Override
	public String handleRequest(Object request, Context context) {
		System.out.println("inicio la ejecujcion......");
		List<String> uuids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			uuids.add(UUID.randomUUID().toString());
		}

		Map<String, Object> result = new HashMap<>();
		result.put("ids", uuids);

		LocalDateTime createdAt = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
		String fileName = createdAt.atZone(ZoneOffset.UTC).format(formatter);
		System.out.println("File name: " + fileName);
		try {
			String jsonContent = objectMapper.writeValueAsString(result);
			s3Client.putObject(bucketName, fileName, jsonContent);
			return "File created: " + fileName;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return "Error: " + e.getMessage();
		}
	}
}