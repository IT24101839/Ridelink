package com.ridelink.account.config;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"jwt.secret=01234567890123456789012345678901","jwt.expiration-ms=600000","logging.level.root=WARN"})
class JwtContractTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @ParameterizedTest @ValueSource(strings={"role","userId","sub","exp","expired","unknownRole"})
    void malformedSignedClaimsAre401(String missing) throws Exception {
        var builder=Jwts.builder();
        if(!missing.equals("sub"))builder.subject("driver@example.com");
        if(!missing.equals("userId"))builder.claim("userId","account-driver");
        if(!missing.equals("role"))builder.claim("role",missing.equals("unknownRole")?"ROOT":"DRIVER");
        if(!missing.equals("exp"))builder.expiration(new Date(missing.equals("expired")?1:System.currentTimeMillis()+60000));
        String token=builder.signWith(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(jwt.isTokenValid(token)).isFalse();
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void issuedDriverClaimsAreStableAndCannotUseAdminApi() throws Exception {
        String token=jwt.generateToken("driver@example.com","DRIVER","account-driver");
        var claims=jwt.extractAllClaims(token);
        assertThat(claims.get("userId",String.class)).isEqualTo("account-driver");
        assertThat(claims.getSubject()).isEqualTo("driver@example.com");
        assertThat(claims.get("role",String.class)).isEqualTo("DRIVER");
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
}
