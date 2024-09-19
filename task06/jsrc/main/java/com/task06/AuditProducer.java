package com.task06;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = false,
	runtime = DeploymentRuntime.JAVA17,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 1
)
@DependsOn(
		name = "Configuration",
		resourceType = ResourceType.DYNAMODB_TABLE
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "Audit")}
)
public class AuditProducer implements RequestHandler<DynamodbEvent, String> {

	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table auditTable = dynamoDB.getTable(System.getenv("target_table"));

	@Override
	public String handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbStreamRecord record : event.getRecords()) {
			String eventName = record.getEventName();
			//System.out.println("Tabla: " + auditTable.getTableName());
			Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
			String itemKeyValue = newImage.get("key").getS();
			Map<String, Object> newImageMap = new HashMap<String, Object>();
			newImageMap.put("key", itemKeyValue);
			newImageMap.put("value", Integer.valueOf(newImage.get("value").getN()));


			Map<String, AttributeValue> oldImage = record.getDynamodb().getOldImage();
			LocalDate createdAt = LocalDate.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
			String isoDateTime = createdAt.atStartOfDay().atOffset(ZoneOffset.UTC).format(formatter);
			try {
				Item auditItem = new Item()
						.withPrimaryKey("id", UUID.randomUUID().toString())
						.withString("itemKey", itemKeyValue)
						.withString("modificationTime", isoDateTime);

				if ("INSERT".equals(eventName)) {
					//System.out.println("Insertar en tabla ... ");
					//newImage.forEach((key, value) -> System.out.println("[Key] : " + key + " [Value] : " + value));
					auditItem.with("newValue", newImageMap);
					//System.out.println("salio Insertar en tabla ... ");
				} else if ("MODIFY".equals(eventName)) {
					//System.out.println("Modificar en tabla ... ENTRO");
					//newImage.forEach((key, value) -> System.out.println("[Key] : " + key + " [Value] : " + value));
					//oldImage.forEach((key, value) -> System.out.println("[Key] : " + key + " [Value] : " + value));
					auditItem.withString("updatedAttribute", "value")
							.withString("oldValue", oldImage.get("value").toString())
							.withString("newValue", newImage.get("value").toString());
					//System.out.println("Modificar en tabla ... SALIO");
				}

				//System.out.println("GRABAR en tabla ... ");
				auditTable.putItem(auditItem);
			} catch (Exception e) {
				context.getLogger().log("Error: " + e.getMessage());
			}
		}
		return "Processed " + event.getRecords().size() + " records.";
	}
}