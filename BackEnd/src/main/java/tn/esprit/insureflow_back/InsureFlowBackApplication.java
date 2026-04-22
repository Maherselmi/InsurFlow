package tn.esprit.insureflow_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync // 🔥 AJOUT ICI

@SpringBootApplication
public class InsureFlowBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsureFlowBackApplication.class, args);
    }

}
