package com.ridelink.drivervehicle;

import com.fasterxml.jackson.databind.*;
import com.ridelink.drivervehicle.model.*;
import com.ridelink.drivervehicle.repository.*;
import com.ridelink.drivervehicle.service.DriverService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.tools.ToolProvider;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "security.jwt-secret=01234567890123456789012345678901",
    "security.internal-token=driver-test-token", "spring.data.mongodb.uri=mongodb://127.0.0.1:1/unreachable","logging.level.root=WARN"})
@org.springframework.test.context.ActiveProfiles("local-integration")
@AutoConfigureMockMvc
class DriverLocalIntegrationTest {
    static final String SECRET="01234567890123456789012345678901";
    static final String BODY="""
        {"fullName":"Driver One","email":"one@example.com","phone":"0771234567",
         "licenseNumber":"LIC-1","serviceAreas":["Colombo"]}
        """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;
    @Autowired DriverService service;
    @TempDir Path compiledAccount;
    @BeforeEach void clear(){vehicles.deleteAll();drivers.deleteAll();}
    String token(String id,String role){
        return "Bearer "+Jwts.builder().subject("one@example.com").claim("userId",id).claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    String create() throws Exception {
        String json=mvc.perform(post("/api/v1/drivers").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("account-1")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }
    String eligible() throws Exception {
        String id=create();
        mvc.perform(post("/api/v1/vehicles").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"driverId":"%s","plateNumber":"ABC-1234","make":"Toyota","model":"Aqua","year":2020,
                 "color":"Blue","type":"CAR","seatCapacity":4}
                """.formatted(id))).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/drivers/"+id+"/availability").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}")).andExpect(status().isOk());
        return id;
    }
    @Test void actualAccountJwtCreatesOwnProfile() throws Exception {
        // Compile the two real Account signing classes, not a copied token implementation.
        Path account=Path.of("../account-service/src/main/java/com/ridelink/account");
        int code=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                "-d",compiledAccount.toString(),account.resolve("config/JwtProperties.java").toString(),
                account.resolve("service/JwtService.java").toString());
        assertThat(code).isZero();
        try(var loader=new URLClassLoader(new java.net.URL[]{compiledAccount.toUri().toURL()},getClass().getClassLoader())){
            Class<?> props=loader.loadClass("com.ridelink.account.config.JwtProperties");
            Object config=props.getConstructor(String.class,long.class).newInstance(SECRET,600000L);
            Class<?> jwt=loader.loadClass("com.ridelink.account.service.JwtService");
            Object signer=jwt.getConstructor(props).newInstance(config);
            String signed=(String)jwt.getMethod("generateToken",String.class,String.class,String.class)
                    .invoke(signer,"one@example.com","DRIVER","actual-account-id");
            mvc.perform(post("/api/v1/drivers").header("Authorization","Bearer "+signed)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accountId").value("actual-account-id"));
        }
    }
    @Test void spoofedAccountIdIsRejected() throws Exception {
        mvc.perform(post("/api/v1/drivers").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"fullName\"", "\"accountId\":\"victim\",\"fullName\"")))
                .andExpect(status().isBadRequest());
        assertThat(drivers.count()).isZero();
    }
    @Test void missingInvalidExpiredAndWrongRole() throws Exception {
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/drivers").header("Authorization","Bearer bad").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        String expired=Jwts.builder().claim("userId","a").claim("role","DRIVER").expiration(new Date(1))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(post("/api/v1/drivers").header("Authorization","Bearer "+expired).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/drivers").header("Authorization",token("a","PASSENGER")).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }
    @Test void duplicateProfileIs409() throws Exception {
        create();
        mvc.perform(post("/api/v1/drivers").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }
    @Test void ownershipProtectsProfilesAndVehicles() throws Exception {
        String id=create();
        mvc.perform(get("/api/v1/drivers/"+id).header("Authorization",token("other","DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/drivers/account/account-1").header("Authorization",token("other","DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vehicles/driver/"+id).header("Authorization",token("other","DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/drivers/"+id).header("Authorization",token("admin","ADMIN"))).andExpect(status().isOk());
    }
    @Test void availabilityRequiresActiveVehicle() throws Exception {
        String id=create();
        mvc.perform(patch("/api/v1/drivers/"+id+"/availability").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}")).andExpect(status().isConflict());
    }
    @Test void internalLookupAndMappingAndTokenSeparation() throws Exception {
        String id=eligible();
        mvc.perform(get("/api/internal/drivers/available")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/drivers/available").header("X-Service-Token","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/drivers/available").header("X-Service-Token","driver-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/internal/drivers/account/account-1").header("X-Service-Token","driver-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/internal/drivers/missing").header("X-Service-Token","driver-test-token")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/drivers/"+id).header("X-Service-Token","driver-test-token")).andExpect(status().isUnauthorized());
    }
    @Test void reservationRetriesAndMatchingRelease() throws Exception {
        String id=eligible();
        for(int n=0;n<2;n++)mvc.perform(put("/api/internal/drivers/"+id+"/reservation").header("X-Service-Token","driver-test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BUSY"));
        mvc.perform(put("/api/internal/drivers/"+id+"/reservation").header("X-Service-Token","driver-test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-2\"}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/internal/drivers/"+id+"/reservation/ride-2").header("X-Service-Token","driver-test-token")).andExpect(status().isConflict());
        for(int n=0;n<2;n++)mvc.perform(delete("/api/internal/drivers/"+id+"/reservation/ride-1").header("X-Service-Token","driver-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
    @Test void releasePreservesExplicitOffline() throws Exception {
        String id=eligible();service.reserve(id,"r1");
        mvc.perform(patch("/api/v1/drivers/"+id+"/availability").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}")).andExpect(status().isOk());
        assertThat(service.release(id,"r1").getStatus()).isEqualTo(DriverStatus.OFFLINE);
    }
    @Test void atomicUpdatePreventsConcurrentReservations() throws Exception {
        String id=eligible();
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(String ride:List.of("r1","r2"))results.add(pool.submit(()->{
                start.await();
                try{service.reserve(id,ride);return true;}
                catch(org.springframework.web.server.ResponseStatusException ex){assertThat(ex.getStatusCode().value()).isEqualTo(409);return false;}
            }));
            start.countDown();
            int won=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))won++;
            assertThat(won).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void locationValidationAndAreas() throws Exception {
        String id=create();
        mvc.perform(patch("/api/v1/drivers/"+id+"/location").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentLat\":91,\"currentLng\":0}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/drivers/"+id+"/location").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentLat\":6.9,\"currentLng\":79.8}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/drivers/"+id+"/service-areas").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"serviceAreas\":[\"Kandy\"]}")).andExpect(status().isOk());
    }
    @Test void mongoPersistsProfileVehicleAndUpdates() throws Exception {
        String id=eligible();
        Driver saved=drivers.findById(id).orElseThrow();
        assertThat(saved.getId()).isNotEqualTo(saved.getAccountId());
        assertThat(saved.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicles.findByDriverId(id)).singleElement().satisfies(v->{
            assertThat(v.getPlateNumber()).isEqualTo("ABC-1234");
            assertThat(v.getYear()).isEqualTo(2020);
        });
        service.location(id,new com.ridelink.drivervehicle.dto.DriverRequests.Location(6.9,79.8),
                new com.ridelink.drivervehicle.security.UserPrincipal("account-1","DRIVER"));
        assertThat(drivers.findById(id).orElseThrow().getCurrentLat()).isEqualTo(6.9);
    }
    @Test void mongoIndexesEnforceBusinessUniqueness() throws Exception {
        String id=eligible();
        assertThatThrownBy(()->drivers.insert(new Driver("account-1","Other","o@example.com","1","LIC-2",Set.of())))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(()->drivers.insert(new Driver("account-2","Other","o@example.com","1","LIC-1",Set.of())))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(()->vehicles.insert(new Vehicle(id,"ABC-1234","M","M",2020,"Blue","CAR",4)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
    @Test void availabilityCannotClearReservation() throws Exception {
        String id=eligible();service.reserve(id,"ride-1");
        mvc.perform(patch("/api/v1/drivers/"+id+"/availability").header("Authorization",token("account-1","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}")).andExpect(status().isConflict());
        assertThat(drivers.findById(id).orElseThrow().getReservedRideId()).isEqualTo("ride-1");
    }
}
