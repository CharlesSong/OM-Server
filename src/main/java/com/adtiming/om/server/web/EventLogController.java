// Copyright 2020 ADTIMING TECHNOLOGY COMPANY LIMITED
// Licensed under the GNU Lesser General Public License Version 3

package com.adtiming.om.server.web;

import com.adtiming.om.pb.CommonPB;
import com.adtiming.om.server.dto.EventLogRequest;
import com.adtiming.om.server.dto.LrRequest;
import com.adtiming.om.server.dto.Placement;
import com.adtiming.om.server.service.AppConfig;
import com.adtiming.om.server.service.CacheService;
import com.adtiming.om.server.service.GeoService;
import com.adtiming.om.server.service.LogService;
import com.adtiming.om.server.util.Compressor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class EventLogController extends BaseController {

    private static final Logger LOG = LogManager.getLogger();
    private static final Set<Integer> NEED_CONVERT_EID_SET = Stream.of(501, 502, 503).collect(Collectors.toSet());

    @Resource
    private AppConfig cfg;

    @Resource
    private GeoService geoService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private CacheService cacheService;

    @Resource
    private LogService logService;

    /**
     * for SDK Event Log
     */
    @PostMapping(value = "/log", params = "v=1")
    @ResponseBody
    public ResponseEntity<?> log(HttpServletRequest req,
                                 @RequestParam("v") int version, // api version
                                 @RequestParam("plat") int plat, // platform (0:iOS,1:Android)
                                 @RequestParam("sdkv") String sdkv,
                                 @RequestParam("k") String appKey,
                                 @RequestBody byte[] data) {
        EventLogRequest o;
        try {
            o = objectMapper.readValue(Compressor.gunzip2s(data), EventLogRequest.class);
            o.setApiv(version);
            o.setSdkv(sdkv);
            o.setPlat(plat);
            o.setGeo(geoService.getGeoData(req));
            o.setAppConfig(cfg);
        } catch (java.util.zip.ZipException | JsonProcessingException e) {
            LOG.warn("log decode fail {}, {}", req.getQueryString(), e.toString());
            return ResponseEntity.badRequest().body("bad data");
        } catch (Exception e) {
            LOG.error("log decode fail {}", req.getQueryString(), e);
            return ResponseEntity.badRequest().body("bad data 2");
        }

        o.setPubApp(cacheService.getPublisherApp(appKey));

        long tsSc = o.getServerTs() - o.getTs();// client less than server ts
        // Tile all events
        for (EventLogRequest.Event event : o.events) {
            long clientTs = event.ts;
            event.serverTs = clientTs + tsSc;
            if (event.msg != null) {
                event.msg = StringUtils.replace(event.msg, "\n", "<br>");
            }

            Placement placement = cacheService.getPlacement(event.pid);
            if (placement != null) {
                event.adType = placement.getAdTypeValue();
                event.abt = CommonPB.ABTest.None_VALUE; //cacheService.getAbTestMode(placement, o.getDid());
            }

            if (event.price > 0F) {
                event.price = cacheService.getUsdMoney(event.cur, event.price);
            }

            // add below event to lr log
            // CALLED_SHOW            501
            // CALLED_IS_READY_TRUE   502
            // CALLED_IS_READY_FALSE  503
            if (NEED_CONVERT_EID_SET.contains(event.eid)) {
                LrRequest lr = o.copyTo(new LrRequest());
                lr.setType(event.eid);
                lr.setMid(event.mid);
                lr.setPid(event.pid);
                lr.setIid(event.iid);
                lr.setScene(event.scene);
                lr.setAbt(event.abt);
                lr.writeToLog(logService);
            }
        }

        o.writeToLog(logService);
        return ResponseEntity.ok().build();
    }

}
