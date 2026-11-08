package com.moimzang.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc 공통 설정. 문서는 {도메인}Api 인터페이스에 달고 Controller는 구현만 한다. */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("모임장 API")
                        .version("v1")
                        .description("동아리·학회 운영진이 일정·부원 명단·출석·정산·회의·자료를 관리하는 서비스"));
    }
}
