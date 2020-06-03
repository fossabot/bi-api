package org.breedinginsight.api.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.brapi.client.v2.model.exceptions.HttpNotFoundException;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.phenotyping.model.*;
import org.brapi.v2.phenotyping.model.request.VariablesRequest;
import org.breedinginsight.brapi.BrAPIProvider;
import org.breedinginsight.brapi.BrAPiClientType;
import org.breedinginsight.dao.db.tables.daos.*;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.mockito.Mockito.*;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitControllerIntegrationTest {

    @Inject
    DSLContext dsl;
    @Inject
    ProgramDao programDao;
    @Inject
    TraitDao traitDao;
    @Inject
    ProgramOntologyDao programOntologyDao;
    @Inject
    MethodDao methodDao;
    @Inject
    ScaleDao scaleDao;
    @Inject
    BrAPIProvider brAPIProvider;
    @Mock
    VariablesAPI variablesAPI;

    Trait validTrait;
    List<Trait> validTraits;
    ProgramEntity validProgram;
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @MockBean(BrAPIProvider.class)
    public BrAPIProvider brAPIProvider() { return mock(BrAPIProvider.class); }

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        MockitoAnnotations.initMocks(this);

        // Insert our traits into the db
        //TODO: Replace with TraitService when implemented
        var fp = FannyPack.fill("src/test/resources/sql/TraitControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));

        // Insert method
        dsl.execute(fp.get("InsertMethod"));

        // Insert scale
        dsl.execute(fp.get("InsertScale"));

        // Insert trait
        dsl.execute(fp.get("InsertTrait"));

        // Insert method
        dsl.execute(fp.get("InsertMethod1"));

        // Insert scale
        dsl.execute(fp.get("InsertScale1"));

        // Insert trait
        dsl.execute(fp.get("InsertTrait1"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);
        ProgramOntologyEntity programOntologyEntity = programOntologyDao.fetchByProgramId(validProgram.getId()).get(0);
        List<TraitEntity> traitEntities = traitDao.fetchByProgramOntologyId(programOntologyEntity.getId());
        validTraits = new ArrayList<>();
        for (TraitEntity traitEntity: traitEntities) {
            Trait trait = new Trait(traitEntity);
            MethodEntity methodEntity = methodDao.fetchOneById(trait.getMethodId());
            Method method = new Method(methodEntity);
            ScaleEntity scaleEntity = scaleDao.fetchOneById(trait.getScaleId());
            Scale scale = new Scale(scaleEntity);
            trait.setMethod(method);
            trait.setScale(scale);
            validTraits.add(trait);
        }
    }

    @Test
    public void getTraitsSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        Boolean trait1Found = false;
        Boolean trait2Found = false;
        for (JsonElement traitJson: data) {
            JsonObject trait = (JsonObject) traitJson;
            String traitId = trait.get("id").getAsString();
            if (traitId.equals(validTraits.get(0).getId().toString())){
                trait1Found = true;
                checkTraitResponse(trait, validTraits.get(0));
            } else if (traitId.equals(validTraits.get(1).getId().toString())) {
                trait2Found = true;
                checkTraitResponse(trait, validTraits.get(1));
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }
    }

    @Test
    @SneakyThrows
    public void getTraitsFullSuccess() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
            validTraits.get(0).getScaleId());
        BrApiVariable brApiVariable2 = getTestBrApiVariable(validTraits.get(1).getId(), validTraits.get(1).getMethodId(),
                validTraits.get(1).getScaleId());

        List<BrApiVariable> brApiVariables = List.of(brApiVariable1, brApiVariable2);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPiClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        Boolean trait1Found = false;
        Boolean trait2Found = false;
        for (JsonElement traitJson: data) {
            JsonObject trait = (JsonObject) traitJson;
            String traitId = trait.get("id").getAsString();
            if (traitId.equals(validTraits.get(0).getId().toString())){
                trait1Found = true;
                checkTraitFullResponse(trait, validTraits.get(0), brApiVariable1);
            } else if (traitId.equals(validTraits.get(1).getId().toString())) {
                trait2Found = true;
                checkTraitFullResponse(trait, validTraits.get(1), brApiVariable2);
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }

    }

    @Test
    public void getTraitsProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void getTraitsBadFullQueryParam() {
        //TODO: Need to fix this
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=no").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void getTraitsBrAPIError() {

        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenThrow(new HttpNotFoundException("test"));
        when(brAPIProvider.getVariablesAPI(BrAPiClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());

    }

    @Test
    @SneakyThrows
    public void getTraitSingleSuccess() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
                validTraits.get(0).getScaleId());
        List<BrApiVariable> brApiVariables = List.of(brApiVariable1);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables(any(VariablesRequest.class))).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPiClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkTraitFullResponse(result, validTraits.get(0), brApiVariable1);
    }

    @Test
    public void getTraitSingleNoExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + invalidUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void getTraitSingleBrAPIError() {

        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenThrow(new HttpNotFoundException("test"));
        when(brAPIProvider.getVariablesAPI(BrAPiClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    public void getTraitSingleProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    public BrApiVariable getTestBrApiVariable(UUID variableId, UUID methodId, UUID scaleId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(variableId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);

        return BrApiVariable.builder()
                .observationVariableDbId("123")
                .contextOfUse(List.of("Nursery Evaluation", "Trial Evaluation"))
                .commonCropName("Tomatillo")
                .defaultValue("defaultValue")
                .documentationURL("http://breedinginsight.org")
                .externalReferences(externalReferenceList)
                .growthStage("flowering")
                .method(getTestBrApiMethod(methodId))
                .scale(getTestBrApiScale(scaleId))
                .scientist("Dr. Jekyll")
                .status("active")
                .trait(getTestBrApiTrait())
                .build();
    }

    public BrApiMethod getTestBrApiMethod(UUID methodId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(methodId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);
        BrApiMethod brApiMethod = BrApiMethod.builder()
                .methodClass("Counting")
                .externalReferences(externalReferenceList)
                .build();
        return brApiMethod;
    }

    public BrApiScale getTestBrApiScale(UUID scaleId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(scaleId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);
        BrApiScale brApiScale = BrApiScale.builder()
                .decimalPlaces(3)
                .dataType(BrApiTraitDataType.TEXT)
                .externalReferences(externalReferenceList)
                .build();
        return brApiScale;
    }

    public BrApiTrait getTestBrApiTrait() {
        BrApiTrait brApiTrait = BrApiTrait.builder()
                .traitClass("morphological")
                .traitDescription("A trait")
                .alternativeAbbreviations(List.of("t1", "t2"))
                .mainAbbreviation("t1")
                .attribute("height")
                .entity("stalk")
                .status("active")
                .synonyms(List.of("stalk height"))
                .build();

        return brApiTrait;
    }

    public void checkTraitResponse(JsonObject traitJson, Trait trait) {

        assertEquals(traitJson.get("id").getAsString(), trait.getId().toString(), "Ids don't match");
        assertEquals(traitJson.get("traitName").getAsString(), trait.getTraitName(), "Names don't match");
        assertEquals(traitJson.get("active").getAsString(), trait.getActive().toString(), "Actives don't match");

        JsonObject method = traitJson.getAsJsonObject("method");
        assertEquals(method.get("methodName").getAsString(), trait.getMethod().getMethodName(), "Method names don't match");

        JsonObject scale = traitJson.getAsJsonObject("scale");
        assertEquals(scale.get("scaleName").getAsString(), trait.getScale().getScaleName(), "Scale names don't match");
        assertEquals(scale.get("dataType").getAsString(), trait.getScale().getDataType().toString(), "Scale data types don't match");

        JsonObject programOntology = traitJson.getAsJsonObject("programOntology");
        assertEquals(programOntology.get("id").getAsString(), trait.getProgramOntologyId().toString(), "Program Ontology ids don't match");
    }

    public void checkTraitFullResponse(JsonObject traitJson, Trait trait, BrApiVariable brApiVariable) {

        // Check values from our db
        checkTraitResponse(traitJson, trait);

        assertEquals(traitJson.get("traitClass").getAsString(), brApiVariable.getTrait().getTraitClass(), "Trait classes don't match");
        assertEquals(traitJson.get("description").getAsString(), brApiVariable.getTrait().getTraitDescription(), "Trait descriptions don't match");

        List<String> jsonAlternativeAbbreviations = new ArrayList<>();
        traitJson.get("abbreviations").getAsJsonArray().iterator().forEachRemaining(element -> jsonAlternativeAbbreviations.add(element.getAsString()));
        assertLinesMatch(jsonAlternativeAbbreviations, brApiVariable.getTrait().getAlternativeAbbreviations(), "Alternative abbreviations don't match");

        assertEquals(traitJson.get("mainAbbreviation").getAsString(), brApiVariable.getTrait().getMainAbbreviation(), "Trait main abbreviations don't match");
        assertEquals(traitJson.get("attribute").getAsString(), brApiVariable.getTrait().getAttribute(), "Trait attributes don't match");
        assertEquals(traitJson.get("entity").getAsString(), brApiVariable.getTrait().getEntity(), "Trait entities don't match");
        String status = traitJson.get("active").getAsBoolean() ? "active" : "inactive";
        assertEquals(status, brApiVariable.getStatus(), "Trait actives don't match");

        List<String> jsonSynonyms = new ArrayList<>();
        traitJson.get("synonyms").getAsJsonArray().iterator().forEachRemaining(element -> jsonSynonyms.add(element.getAsString()));
        assertLinesMatch(jsonSynonyms, brApiVariable.getTrait().getSynonyms(), "Synonyms don't match");

        // Check method
        JsonObject methodJson = traitJson.getAsJsonObject("method");
        assertEquals(methodJson.get("methodClass").getAsString(), brApiVariable.getMethod().getMethodClass(), "Trait main abbreviations don't match");

        // Check scale
        JsonObject scaleJson = traitJson.getAsJsonObject("scale");
        assertEquals(scaleJson.get("decimalPlaces").getAsInt(), brApiVariable.getScale().getDecimalPlaces(), "Trait main abbreviations don't match");
        assertEquals(scaleJson.get("dataType").getAsString(), brApiVariable.getScale().getDataType().toString(), "Trait main abbreviations don't match");
    }

}
