package com.sterul.opencookbookapiserver.controllers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientNutrientInfoResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNutritionalInfo;
import com.sterul.opencookbookapiserver.services.IngredientNutritionalInfoService;

@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientNutritionalInfoController {

    private IngredientNutritionalInfoService ingredientNutritionalInfoService;

    @Value("classpath:ingredientsources/foundationDownload.json")
    private Resource foundationIngredients;

    @Value("classpath:ingredientsources/surveyFood.json")
    private Resource surveyFood;

    @Value("classpath:ingredientsources/foodData.json")
    private Resource foodData;

    public IngredientNutritionalInfoController(IngredientNutritionalInfoService ingredientNutritionalInfoService) {
        this.ingredientNutritionalInfoService = ingredientNutritionalInfoService;
    }

    @GetMapping("/nutritionalInfo")
    public List<IngredientNutrientInfoResponse> getAll() {
        return ingredientNutritionalInfoService.getAll().stream().map(info -> IngredientNutrientInfoResponse.builder()
                .id(info.getId()).energyPerUnit(info.getEnergyPerUnit()).build()).toList();
    }

    @PostMapping("/{id}/nutritionalInfo")
    public IngredientNutritionalInfo save(@PathVariable Long id, @RequestBody IngredientNutritionalInfo info) {
        // TODO: Authorization
        info.setIngredient(Ingredient.builder().id(id).build());
        return ingredientNutritionalInfoService.addNutritionalInfo(info);
    }

    @PostMapping("/nutritionalInfo/parse")
    public void parse() {
        JsonParser springParser = JsonParserFactory.getJsonParser();
        try {
            // var foundationParser = springParser.parseMap(
            // StreamUtils.copyToString(foundationIngredients.getInputStream(),
            // Charset.defaultCharset()));
            // var foundationFoods = (ArrayList<Map>)
            // foundationParser.get("FoundationFoods");
            // foundationFoods.forEach(food -> System.out.println(food.get("description")));

            // System.out.println("survey foods:");

            var categories = Arrays.asList("Finfish and Shellfish Products",
                    "Poultry Products",
                    "Vegetables and Vegetable Products",
                    "Beef Products",
                    "Dairy and Egg Products",
                    "Sausages and Luncheon Meats",
                    "Pork Products",
                    "Beverages",
                    "Legumes and Legume Products",
                    "Breakfast Cereals",
                    "Baked Products",
                    "Fats and Oils",
                    "Cereal Grains and Pasta",
                    "Nut and Seed Products",
                    "Fruits and Fruit Juices");

            var cats = new HashMap<String, Boolean>();

            var reader = new JsonReader(new InputStreamReader(foodData.getInputStream()));
            while (reader.hasNext()) {
                var token = reader.peek();

                reader.beginObject();

                reader.nextName();

                reader.beginArray();

                while (reader.hasNext()) {

                    // Actual ingredient
                    reader.beginObject();

                    while (reader.peek().equals(JsonToken.NAME)) {
                        // Skip everything until foodCategory
                        if (reader.nextName().equals("description")) {
                            break;
                        } else {
                            reader.skipValue();
                        }
                    }

                    var description = reader.nextString();

                    while (reader.peek().equals(JsonToken.NAME)) {
                        // Skip everything until foodCategory
                        if (reader.nextName().equals("foodNutrients")) {
                            break;
                        } else {
                            reader.skipValue();
                        }
                    }

                    // Nutrient array
                    reader.beginArray();

                    // Loop through all nutrients
                    double calories = 0;
                    while (reader.peek() != JsonToken.END_ARRAY) {
                        // First nutrient
                        reader.beginObject();

                        while (reader.peek().equals(JsonToken.NAME)) {
                            // Skip everything until foodCategory
                            if (reader.nextName().equals("nutrient")) {
                                break;
                            } else {
                                reader.skipValue();
                            }
                        }

                        // Nutrient definition
                        reader.beginObject();

                        while (reader.peek().equals(JsonToken.NAME)) {
                            // Skip everything until foodCategory
                            if (reader.nextName().equals("name")) {
                                break;
                            } else {
                                reader.skipValue();
                            }
                        }

                        var nutrientName = reader.nextString();
                        while (!reader.peek().equals(JsonToken.END_OBJECT)) {
                            reader.skipValue();
                        }
                        reader.endObject();

                        while (reader.peek().equals(JsonToken.NAME)) {
                            // Skip everything until foodCategory
                            if (reader.nextName().equals("amount")) {
                                break;
                            } else {
                                reader.skipValue();
                            }
                        }

                        // TODO: Only if nutrientName is Energy
                        // TODO: kJ or kcal
                        // TODO: per 100g?
                        if ("Energy".equals(nutrientName)) {
                            calories = reader.nextDouble();
                        }

                        while (!reader.peek().equals(JsonToken.END_OBJECT)) {
                            reader.skipValue();
                        }

                        // End nutrient object
                        reader.endObject();
                    }

                    // End nutrients array
                    reader.endArray();

                    

                    while (reader.peek().equals(JsonToken.NAME)) {
                        // Skip everything until foodCategory
                        if (reader.nextName().equals("foodCategory")) {
                            break;
                        } else {
                            reader.skipValue();
                        }
                    }

                    // TODO: Needed?
                    if (!reader.peek().equals(JsonToken.BEGIN_OBJECT)) {
                        // No nutrients, skip to next ingredient
                        reader.endArray();
                        while (!reader.peek().equals(JsonToken.END_OBJECT)) {
                            reader.skipValue();
                        }
                        reader.endObject();
                        System.out.println("No category for " + description);
                        continue;
                    }

                    reader.beginObject();

                    // Should be "description" (of category)
                    reader.nextName();

                    // The actual category
                    var category = reader.nextString();

                    cats.putIfAbsent(category, true);


                    if (categories.contains(category)) {
                        System.out.println(description + " kalories: " + calories);
                    }


                    reader.endObject();

                    // Skip until next ingredient
                    while (reader.peek() != JsonToken.END_OBJECT) {
                        reader.skipValue();
                    }
                    reader.endObject();
                }
            }

            // var surveyParser = springParser
            // .parseMap(StreamUtils.copyToString(foodData.getInputStream(),
            // Charset.defaultCharset()));
            // var surveyFoods = (ArrayList<Map>) surveyParser.get("SurveyFoods");
            // var cats = new HashMap<String, Boolean>();
            // surveyFoods.stream().map(food -> {
            // var category = (Map<String, String>) food.get("foodCategory");
            // if (category == null) {
            // return "no category";
            // }
            // return category.get("description");
            // }).forEach(cat -> cats.putIfAbsent(cat, true));

            System.out.println("Categories:");
            cats.keySet().stream().forEach(cat -> System.out.println(cat));

        } catch (JsonParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}
