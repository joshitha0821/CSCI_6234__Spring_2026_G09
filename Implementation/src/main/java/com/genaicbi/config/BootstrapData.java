package com.genaicbi.config;

import com.genaicbi.service.TrainingDataService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapData implements ApplicationRunner {

    private final TrainingDataService trainingDataService;

    public BootstrapData(TrainingDataService trainingDataService) {
        this.trainingDataService = trainingDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        trainingDataService.addExample(
                "what were last month sales by region",
                """
                        SELECT region, SUM(amount) AS total_sales
                        FROM sales
                        WHERE order_date >= DATEADD('MONTH', -1, CURRENT_DATE())
                        GROUP BY region
                        ORDER BY total_sales DESC
                        LIMIT 20
                        """
        );
        trainingDataService.addExample(
                "top customers by sales",
                """
                        SELECT customer, SUM(amount) AS total_sales
                        FROM sales
                        GROUP BY customer
                        ORDER BY total_sales DESC
                        LIMIT 10
                        """
        );
    }
}
