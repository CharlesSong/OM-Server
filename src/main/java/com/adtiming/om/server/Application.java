// Copyright 2020 ADTIMING TECHNOLOGY COMPANY LIMITED
// Licensed under the GNU Lesser General Public License Version 3

package com.adtiming.om.server;

import com.adtiming.om.server.dto.NodeConfig;
import com.adtiming.om.server.service.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@SpringBootApplication(scanBasePackages = {"com.adtiming.om.server"})
@EnableScheduling
public class Application {

    private static final Logger LOG = LogManager.getLogger();

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpAsyncClient httpAsyncClient() {
        return HttpAsyncClients.custom()
                .setMaxConnPerRoute(50000)
                .setMaxConnTotal(100000)
                .setUserAgent("om-server/1.0.1")
                .build();
    }

    @Bean
    public NodeConfig nc(@Autowired AppConfig cfg,
                         @Autowired ObjectMapper objectMapper) {
        try {
            String nodeid;
            Path nodeidPath = Paths.get("data/nodeid");
            if (Files.exists(nodeidPath)) {
                nodeid = new String(Files.readAllBytes(nodeidPath), UTF_8);
            } else {
                nodeid = UUID.randomUUID().toString();
                if (Files.notExists(nodeidPath.getParent())) {
                    Files.createDirectories(nodeidPath.getParent());
                }
                Files.write(nodeidPath, nodeid.getBytes(UTF_8));
            }

            String url = String.format("http://%s:19012/snode/config/get?nodeid=%s&dcenter=%d",
                    cfg.getDtask(), nodeid, cfg.getDcenter());
            NodeConfig nc = objectMapper.readValue(new URL(url), NodeConfig.class);
            cfg.setSnode(nc.id);
            LOG.info("OM-Server init, snode: {}, dc: {}, dtask: {}, {}",
                    nc.id, cfg.getDcenter(), cfg.getDtask(), nc);
            return nc;
        } catch (Exception e) {
            LOG.error("load snode/config from dtask error", e);
            System.exit(1);
        }
        return new NodeConfig();
    }

}
