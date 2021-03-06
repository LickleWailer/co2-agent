package car.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import util.CO2FootprintProperties;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class RestConsumer {

	private final CO2FootprintProperties properties;

	public RestConsumer(CO2FootprintProperties properties) {
		this.properties = properties;
	}

	public ObjectNode getBrandsAsJson() throws JsonProcessingException {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		// request brands from remote database
		Client client = ClientBuilder.newClient();
		String url = "https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/aggregates?select=marque&group_by=marque";
		WebTarget webTarget = client.target(url);
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
		Response response = invocationBuilder.get();

		// retrieve brands from API response and create new JSON string
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode responseNode = objectMapper.readTree(response.readEntity(String.class));
		ObjectNode resultNode = objectMapper.createObjectNode();
		ArrayNode addedNode = resultNode.putArray("brands");

		for (JsonNode jsonNode : responseNode.get("aggregations")) addedNode.add(jsonNode.get("marque"));

		client.close();
		return resultNode;
	}

	public ObjectNode getModelsAsJson(String brand) throws JsonProcessingException {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		String url = "https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/aggregates?select=designation_commerciale&group_by=designation_commerciale&where=marque%20like%20%22"
				+ brand
				+ "%22";

		url = url.replaceAll(" ", "%20");

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(url);
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
		Response response = invocationBuilder.get();

		// retrieve brands from API response and create new JSON string
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode responseNode = objectMapper.readTree(response.readEntity(String.class));
		ObjectNode resultNode = objectMapper.createObjectNode();
		ArrayNode addedNode = resultNode.putArray("model");

		for (JsonNode jsonNode : responseNode.get("aggregations"))
			addedNode.add(jsonNode.get("designation_commerciale"));

		client.close();
		return resultNode;

	}

	public ObjectNode getFuelAsJson(String brand, String model) throws JsonProcessingException {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		String url = "https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/aggregates?select=carburant&group_by=carburant&where=marque%20like%20%22" +
				brand +
				"%22%20and%20designation_commerciale%20like%20%22" +
				model +
				"%22";

		url = url.replaceAll(" ", "%20");

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(url);
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
		Response response = invocationBuilder.get();

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode responseNode = objectMapper.readTree(response.readEntity(String.class));
		ObjectNode resultNode = objectMapper.createObjectNode();
		ArrayNode addedNode = resultNode.putArray("fuel");

		for (JsonNode jsonNode : responseNode.get("aggregations")) {
			String fuel = translateFuelToAgentRepresentation(jsonNode.get("carburant").asText());
			addedNode.add(fuel);
		}

		client.close();
		return resultNode;
	}

	public ObjectNode getCarIdAsJson(String brand, String model, String fuel) throws JsonProcessingException {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode resultNode = objectMapper.createObjectNode();

		fuel = translateFuelToDatabaseRepresentation(fuel);
		String url = "https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/records?where=marque%20like%20%22" +
				brand +
				"%22%20and%20designation_commerciale%20like%20%22" +
				model +
				"%22%20and%20carburant%20like%20%22" +
				translateFuelToDatabaseRepresentation(fuel) +
				"%22&rows=1&pretty=false&timezone=UTC";

		url = url.replaceAll(" ", "%20");

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(url);
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
		Response response = invocationBuilder.get();

		JsonNode responseNode = objectMapper.readTree(response.readEntity(String.class));
		resultNode.put("id", responseNode.get("records").findValuesAsText("id").get(0));

		client.close();
		return resultNode;
	}

	public HashMap<String, String> getCarData(String carID) throws JsonProcessingException {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		HashMap<String, String> carMap = new HashMap<>();

		String url = "https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/records/"
				+ carID
				+ "?pretty=false&timezone=UTC";

		url = url.replaceAll(" ", "%20");

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(url);
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
		Response response = invocationBuilder.get();

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode responseNode = objectMapper.readTree(response.readEntity(String.class));
		ObjectNode objectNode = (ObjectNode) responseNode.get("record");

		carMap.put("id", objectNode.get("id").asText());
		carMap.put("urbanConsumption", objectNode.get("fields").get("consommation_urbaine_l_100km").asText());
		carMap.put("nonUrbanConsumption", objectNode.get("fields").get("consommation_extra_urbaine_l_100km").asText());
		carMap.put("autobahnConsumption", objectNode.get("fields").get("consommation_extra_urbaine_l_100km").asText());
		carMap.put("officialCO2", objectNode.get("fields").get("co2_g_km").asText());
		carMap.put("fuel", translateFuelToAgentRepresentation(objectNode.get("fields").get("carburant").asText()));

		client.close();
		return carMap;
	}

	public InputStream downloadDatabase() throws IOException {
		URL url = new URL("https://data.opendatasoft.com/api/v2/catalog/datasets/vehicules-commercialises%40public/exports/csv?rows=" + properties.getCarDatabaseRows() + "&timezone=UTC&delimiter=%3B");

		URLConnection urlConnection = url.openConnection();

		return urlConnection.getInputStream();
	}

	public static String translateFuelToAgentRepresentation(String fuel) {
		switch (fuel) {
			case "Essence":
				return "petrol";
			case "Diesel":
				return "diesel";
			case "GN":
				return "cng";
			case "EL":
				return "electricity";
		}
		return fuel;
	}

	public static String translateFuelToDatabaseRepresentation(String fuel) {
		switch (fuel) {
			case "petrol":
				return "Essence";
			case "diesel":
				return "Diesel";
			case "cng":
				return "GN";
			case "electricity":
				return "EL";
		}
		return fuel;
	}
}
