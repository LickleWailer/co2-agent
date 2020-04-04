package beans;

import calculation.CO2Calculator;
import calculation.CO2EmissionFactors;
import car.Car;
import car.database.Driver;
import car.database.RestConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;
import de.dailab.jiactng.agentcore.ontology.IActionDescription;
import de.dailab.jiactng.rsga.beans.AbstractRESTfulAgentBean;
import routing.CarRoute;
import routing.Place;
import routing.PublicTransportRoute;
import util.CO2FootprintProperties;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

@SuppressWarnings("DanglingJavadoc")
public class RESTfulBean extends AbstractRESTfulAgentBean {

	private CO2FootprintProperties properties;

	public void doStart() throws Exception {
		super.doStart();
		log.info("RESTfulBean - starting");
		this.properties = new CO2FootprintProperties();
	}

	@Override
	public void doStop() throws Exception {
		super.doStop();
		log.info("RESTfulBean - stopping");
	}



	/*************************************************************/
	/************************** GENERAL **************************/
	/*************************************************************/

	/**
	 * Gets all the available electricity mixes.
	 *  <p>
	 *  Needed for calculating the CO2 emissions of cars powered by electricity, as different electricity mixes provide for
	 *  different levels of emissions.
	 * @return All implemented electricity mixes as a JSON formatted list. Currently {@code de} for the general electricity
	 *  mix of germany and {@code de_eco} for the german electricity mix with a higher share of renewable energy.
	 */
	@GET
	@Path("/util/electricity/mixes")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1ElectricityMixes() {
		log.info("New REST request - v1ElectricityMixes() called");
		return CO2EmissionFactors.getMixesAsJson();
	}






	/*********************************************************************/
	/******************* CAR-RELATED (FROM DB -> "V2") *******************/
	/*********************************************************************/

	/**
	 * Retrieves all available brands from the underlying database.
	 * @return JSON formatted list of brands
	 */
	@GET
	@Path("/v2/cars/brands")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsBrands() {
		log.info("New REST request - v2CarsBrands() called");

		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getBrands");
		IActionDescription act = memory.read(template);

		Serializable[] brands = invokeAndWaitForResult(act, new Serializable[]{}).getResults();

		return brands[0];
	}

	/**
	 * Retrieves all available models for the specified brand from the underlying database.
	 * @param brand One of the brands returned by the {@code getBrands} method
	 * @return JSON formatted list of available models for {@code brand} parameter
	 */
	@GET
	@Path("/v2/cars/brand/models")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsModels(@QueryParam("brand") String brand) {
		log.info("New REST request - v2CarsModels(...) called");
		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getModels");
		IActionDescription act = memory.read(template);

		Serializable[] models = invokeAndWaitForResult(act, new Serializable[]{brand}).getResults();

		return models[0];
	}

	/**
	 * Retrieves all available models for the specified brand and fuel type from the underlying database.
	 * @param brand One of the brands returned by the {@code brands} method
	 * @param fuel One of the fuel types returned by the {@code mixes} method
	 * @return JSON formatted list of available models for {@code brand} and {@code fuel} parameter
	 */
	@GET
	@Path("/v2/cars/brand/fuel/models")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsModelsByFuel(@QueryParam("brand") String brand,
										   @QueryParam("fuel") String fuel) {
		log.info("New REST request - v2CarsModelsByFuel(...) called");
		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getModelsByFuel");
		IActionDescription act = memory.read(template);

		Serializable[] models = invokeAndWaitForResult(act, new Serializable[]{brand, fuel}).getResults();

		return models[0];
	}

	/**
	 * Gets all available drive configurations (fuel to power the car) for the specified brand and model.
	 * @param brand One of the brands returned by the {@code brands} method
	 * @param model One of the models returned by the {@code models} method
	 * @return JSON formatted list of available drive configurations for the {@code brand} and {@code model} parameters.
	 *  Returns a subset or all of the following: {@code {petrol, diesel, cng, electricity}}
	 */
	@GET
	@Path("/v2/cars/brand/model/fuel")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsFuel(@QueryParam("brand") String brand,
								   @QueryParam("model") String model) {
		log.info("New REST request - v2CarsFuel(...) called");
		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getFuel");
		IActionDescription act = memory.read(template);

		Serializable[] fuel = invokeAndWaitForResult(act, new Serializable[]{brand, model}).getResults();

		return fuel[0];
	}

	/**
	 * Gets all available drive configurations (fuel to power the car) for the specified brand.
	 * @param brand One of the brands returned by the {@code brands} method
	 * @return JSON formatted list of available drive configurations for the {@code brand} parameter.
	 *  Returns a subset or all of the following: {@code {petrol, diesel, cng, electricity}}
	 */
	@GET
	@Path("/v2/cars/brand/fuel")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsFuelByBrand(@QueryParam("brand") String brand) {
		log.info("New REST request - v2CarsFuelByBrand(...) called");
		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getFuelByBrand");
		IActionDescription act = memory.read(template);

		Serializable[] fuel = invokeAndWaitForResult(act, new Serializable[]{brand}).getResults();

		return fuel[0];
	}

	/**
	 * Get the unique ID of the car specified by brand, model and fuel.
	 * @param brand One of the brands returned by the {@code brands} method
	 * @param model One of the models returned by the {@code models} method
	 * @param fuel One of the drive configurations returned by the {@code fuel} method
	 * @return JSON formatted id field
	 */
	@GET
	@Path("/v2/cars/brand/model/fuel/id")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public Serializable v2CarsId(@QueryParam("brand") String brand,
								 @QueryParam("model") String model,
								 @QueryParam("fuel") String fuel) {

		log.info("New REST request - v2CarsId(...) called");

		IActionDescription template = new Action("ACTION#beans.CarDatabaseBean.getCarID");
		IActionDescription act = memory.read(template);

		Serializable[] carId = invokeAndWaitForResult(act, new Serializable[]{brand, model, fuel}).getResults();

		return carId[0];
	}






	/**********************************************************************/
	/******************* CAR-RELATED (FROM API -> "V1") *******************/
	/**********************************************************************/

	/**
	 * Retrieves all available brands from the remote open data database.
	 * @return JSON formatted list of brands
	 */
	@GET
	@Path("/v1/cars/brands")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CarsBrands() {
		log.info("New REST request - v1CarsBrands() called");

		RestConsumer restConsumer = new RestConsumer(properties);
		ObjectNode brands = null;
		try {
			brands = restConsumer.getBrandsAsJson();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		restConsumer.close();
		return brands;
	}

	/**
	 * Retrieves all available models for the specified brand from the underlying database.
	 * @param brand One of the brands returned by the {@code getBrands} method
	 * @return JSON formatted list of available models for {@code brand} parameter
	 */
	@GET
	@Path("/v1/cars/brand/models")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CarsModels(@QueryParam("brand") String brand) {
		log.info("New REST request - v1CarsModels(...) called");

		RestConsumer restConsumer = new RestConsumer(properties);
		ObjectNode models = null;
		try {
			models = restConsumer.getModelsAsJson(brand);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		restConsumer.close();
		return models;
	}

	/**
	 * Gets all available drive configurations (fuel to power the car) for the specified brand and model.
	 * @param brand One of the brands returned by the {@code getBrands} method
	 * @param model One of the models returned by the {@code getModels} method
	 * @return JSON formatted list of available drive configurations for the {@code brand} and {@code model} parameters.
	 *  Returns a subset or all of the following: {@code {petrol, diesel, cng, electricity}}
	 */
	@GET
	@Path("/v1/cars/brand/model/fuel")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CarsFuel(@QueryParam("brand") String brand,
							 @QueryParam("model") String model) {

		log.info("New REST request - v1CarsFuel(...) called");

		RestConsumer restConsumer = new RestConsumer(properties);
		ObjectNode fuel = null;
		try {
			fuel = restConsumer.getFuelAsJson(brand, model);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		restConsumer.close();
		return fuel;
	}

	/**
	 * Get the unique ID of the car specified by brand, model and fuel.
	 * @param brand One of the brands returned by the {@code getBrands} method
	 * @param model One of the models returned by the {@code getModels} method
	 * @param fuel One of the drive configurations returned by the {@code getFuel} method
	 * @return JSON formatted id field
	 */
	@GET
	@Path("/v1/cars/brand/model/fuel/id")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CarsId(@QueryParam("brand") String brand,
						   @QueryParam("model") String model,
						   @QueryParam("fuel") String fuel) {

		log.info("New REST request - v1CarsId(...) called");

		RestConsumer restConsumer = new RestConsumer(properties);
		ObjectNode carID = null;
		try {
			carID = restConsumer.getCarIdAsJson(brand, model, fuel);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		restConsumer.close();
		return carID;
	}






	/*************************************************************/
	/************************ CALCULATION ************************/
	/*************************************************************/


	/**
	 * Calculate the CO2 emissions of a car specified by {@code carID} on a route that is identified by the length of
	 *  its parts in urban ({@code urbanKM}) and non-urban ({@code nonUrbanKM}) areas and on the autobahn ({@code autobahnKM}).
	 *  If the car is powered by electricity, the electricity mix ({@code mix}) is needed. Otherwise this parameter is
	 *  simply ignored and can be {@code null}. Note that all lengths must be given in kilometers.
	 * @deprecated Use {@link #v2CalculationEmissionsCar(String, String, double, double, double, double)} instead.
	 * @param carID ID as returned by the {@code getCar} method
	 * @param mix Used electricity mix if the car is powered by electricity. Otherwise {@code null}.
	 * @param urbanKM Travel distance in kilometers within urban areas. Note that this includes all parts of the route
	 *                where the maximum speed is lower than 50 km/h.
	 * @param nonUrbanKM Travel distance in kilometers within non-urban areas. Note that this includes all parts of the
	 *                   route where the maximum speed is between 50 and 100 km/h.
	 * @param autobahnKM Travel distance in kilometers on highways. Note that this includes all parts of the route where
	 *                   the maximum speed is above 100 km/h.
	 * @return JSON formatted field containing the estimated CO2 emissions for the given car and the given route information.
	 */
	@GET
	@Path("/v1/calculation/emissions/car")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CalculationEmissionsCar(@QueryParam("carID") String carID,
											@QueryParam("mix") String mix,
											@QueryParam("urbanKM") double urbanKM,
											@QueryParam("nonUrbanKM") double nonUrbanKM,
											@QueryParam("autobahnKM") double autobahnKM)
	{
		log.info("New REST request - v1CalculationEmissionsCar(...) called");

		Car car = null;

		ArrayList<Car> genericCars = Car.getGenericCars();
		for (Car genericCar : genericCars) {
			if (genericCar.getId().equals(carID)) {
				car = genericCar;
				break;
			}
		}

		if (car == null) car = new Car(carID, properties);
		CarRoute carRoute = new CarRoute(urbanKM, nonUrbanKM, autobahnKM);
		Double emissions = CO2Calculator.calculateCarEmissions(car, carRoute, mix);

		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode result = objectMapper.createObjectNode();
		result.put("carEmissions", emissions);

		return result;
	}

	/**
	 * Calculate the CO2 emissions of a car specified by {@code carID} on a route that is identified by the latitude and
	 *  longitude of its starting point ({@code startLatitude}, {@code startLongitude}) and its destination
	 *  ({@code destinationLatitude}, {@code destinationLongitude}).
	 *  If the car is powered by electricity, the electricity mix ({@code mix}) is needed. Otherwise this parameter is
	 *  simply ignored and can be {@code null}.
	 * @implSpec This method uses information about the shortest route that is found by the Open Route Service API between
	 *  the start and the destination.
	 * @param carID ID as returned by the {@code getCar} method
	 * @param mix Used electricity mix if the car is powered by electricity. Otherwise {@code null}.
	 * @param urbanKM Travel distance in kilometers within urban areas. Note that this includes all parts of the route
	 *                where the maximum speed is lower than 50 km/h.
	 * @param nonUrbanKM Travel distance in kilometers within non-urban areas. Note that this includes all parts of the
	 *                   route where the maximum speed is between 50 and 100 km/h.
	 * @param autobahnKM Travel distance in kilometers on highways. Note that this includes all parts of the route where
	 *                   the maximum speed is above 100 km/h.
	 * @return JSON formatted field containing the estimated CO2 emissions for the given car and the given start/end point.
	 */
	@GET
	@Path("/v2/calculation/length/emissions/car")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v2CalculationEmissionsCarByLength(@QueryParam("carID") String carID,
														@QueryParam("mix") String mix,
														@QueryParam("urbanKM") double urbanKM,
														@QueryParam("nonUrbanKM") double nonUrbanKM,
														@QueryParam("autobahnKM") double autobahnKM)
	{
		log.info("New REST request - v2CalculationEmissionsCarByLength(...) called");

		Driver driver = new Driver(properties);

		Car car = null;

		ArrayList<Car> genericCars = Car.getGenericCars();
		for (Car genericCar : genericCars) {
			if (genericCar.getId().equals(carID)) {
				car = genericCar;
				break;
			}
		}

		try {
			if (car == null) car = driver.getCar(carID);
			driver.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		CarRoute carRoute = new CarRoute(urbanKM, nonUrbanKM, autobahnKM);
		Double emissions = CO2Calculator.calculateCarEmissions(Objects.requireNonNull(car), carRoute, mix);

		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode result = objectMapper.createObjectNode();
		result.put("carEmissions", emissions);

		return result;
	}

	/**
	 * Calculate the CO2 emissions of a car specified by {@code carID} on a route that is identified by the latitude and
	 *  longitude of its starting point ({@code startLatitude}, {@code startLongitude}) and its destination
	 *  ({@code destinationLatitude}, {@code destinationLongitude}).
	 *  If the car is powered by electricity, the electricity mix ({@code mix}) is needed. Otherwise this parameter is
	 *  simply ignored and can be {@code null}.
	 * @implSpec This method uses information about the shortest route that is found by the Open Route Service API between
	 *  the start and the destination.
	 * @param carID ID as returned by the {@code getCar} method
	 * @param mix Used electricity mix if the car is powered by electricity. Otherwise {@code null}.
	 * @param startLatitude Latitude of the routes starting point
	 * @param startLongitude Longitude of the routes starting point
	 * @param destinationLatitude Latitude of the routes destination
	 * @param destinationLongitude Longitude of the routes destination
	 * @return JSON formatted field containing the estimated CO2 emissions for the given car and the given start/end point.
	 */
	@GET
	@Path("/v2/calculation/locations/emissions/car")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v2CalculationEmissionsCar(@QueryParam("carID") String carID,
											@QueryParam("mix") String mix,
											@QueryParam("startLatitude") double startLatitude,
											@QueryParam("startLongitude") double startLongitude,
											@QueryParam("destinationLatitude") double destinationLatitude,
											@QueryParam("destinationLongitude") double destinationLongitude)
	{
		log.info("New REST request - v2CalculationEmissionsCar(...) called");

		Driver driver = new Driver(properties);

		Car car = null;

		ArrayList<Car> genericCars = Car.getGenericCars();
		for (Car genericCar : genericCars) {
			if (genericCar.getId().equals(carID)) {
				car = genericCar;
				break;
			}
		}

		try {
			if (car == null) car = driver.getCar(carID);
			driver.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		Place start = new Place(startLatitude, startLongitude);
		Place destination = new Place(destinationLatitude, destinationLongitude);
		CarRoute carRoute = new CarRoute(start, destination, properties);

		Double emissions = CO2Calculator.calculateCarEmissions(Objects.requireNonNull(car), carRoute, mix);

		// calculate public transport emissions
		PublicTransportRoute route = new PublicTransportRoute(carRoute.getUrbanKM(), carRoute.getNonUrbanKM() + carRoute.getAutobahnKM());
		Double ptEmissions = CO2Calculator.calculatePublicTransportEmissions(route);

		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode result = objectMapper.createObjectNode();
		result.put("carEmissions", emissions);
		result.put("publicTransportEmissions", ptEmissions);

		return result;
	}

	/**
	 * Calculate the CO2 emissions for using public transport (excluding air traffic) on a route, that consists of
	 *  {@code shortDistanceKM} kilometers short distance transportation (local bus traffic, underground and [sub]urban railway)
	 *  and {@code longDistanceKM} kilometers long distance transportation (mainline rail services and regional trains).
	 * @param shortDistanceKM Travel distance in kilometers using local bus traffic, underground and [sub]urban railway
	 * @param longDistanceKM Travel distance in kilometers using regional trains and mainline rail services.
	 * @return Estimated CO2 emissions for the given route information
	 */
	@GET
	@Path("/calculation/emissions/publictransport")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public ObjectNode v1CalculationEmissionsPT(@QueryParam("shortDistanceKM") double shortDistanceKM,
										   @QueryParam("longDistanceKM") double longDistanceKM)
	{
		log.info("New REST request - v1CalculationEmissionsPT(...) called");

		PublicTransportRoute route = new PublicTransportRoute(shortDistanceKM, longDistanceKM);
		Double emissions = CO2Calculator.calculatePublicTransportEmissions(route);

		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode result = objectMapper.createObjectNode();
		result.put("publicTransportEmissions", emissions);

		return result;
	}






	/************************************************************/
	/************************* LOCATION *************************/
	/************************************************************/

	/**
	 * Search for places using an address or the name of a venue and get the latitude and longitude of this place.
	 * @param query Address or name of a venue.
	 * @return JSON formatted list of search results, each with its corresponding latitude and longitude.
	 */
	@GET
	@Path("/locations/search")
	@Produces("application/json")
	@Expose(scope = ActionScope.WEBSERVICE)
	public JsonNode v2LocationsSearch(@QueryParam("query") String query) {
		log.info("New REST request - v2LocationsSearch(...) called");

		JsonNode places = null;
		try {
			places = Place.searchPlace(query, properties);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		return places;
	}

}