package com.prwatech.common.configuration;

import org.keycloak.jose.jwk.JWKParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.PublicKey;

@Configuration
public class PublicKeyBeanConfig {

    @Bean
    public PublicKey publicKeyBean() {

        JWKParser jwkParser = JWKParser.create();
        String key =
                "{\n"
                        + "  \"kid\": \"jpgYPmv6dWd5R4iBSDm09mPptcQQJP-QRWWDJgdzp_8\",\n"
                        + "  \"kty\": \"RSA\",\n"
                        + "  \"alg\": \"RS256\",\n"
                        + "  \"use\": \"sig\",\n"
                        + "  \"n\": \"1PfJ51rvViJ8-w_8EhQRR_Vz9AvkdVspp_-DjOIJrsdvK0agTUxx2HCua14CbcUOENAvsQSzjPrOo8iIzgyM9TbvpOul6kGLoSCUEpaE81qnIl8QtAbmwaSQ4_QKI3aQpgNM_jonG8zyTYHMw0u4Koa4iQ_Oxt3gd8yZpLppnLF8xKuCtugEIm2htR5zItPW99Q7t7yMliKkiQL8abqU6y6bxdzOzwJfJ4I5ZbzkHB0zTv8YaLfnuz1O5hCebnjXPvmjNo_tPS-tpJzxuMjabf_gAKCsQMd_vkRQUel0IHBCs3YA4mEm3vT6NZhWlKq4F_jcphBe5rQJcUqsoo0NKw\",\n"
                        + "  \"e\": \"AQAB\"\n"
                        + "}";
        jwkParser.parse(key);
        return jwkParser.toPublicKey();
    }
}
