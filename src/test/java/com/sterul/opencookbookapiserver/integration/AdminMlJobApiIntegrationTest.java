package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.repositories.MlJobRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.ml.MlJobService;

/** The subsystem is configured but unreachable: the situation stuck scans are cleared in. */
@SpringBootTest(properties = {
        "opencookbook.ml.service-url=http://127.0.0.1:1",
        "opencookbook.ml.api-token=cpml_test_token",
        "opencookbook.ml.connect-timeout-seconds=1",
        "opencookbook.ml.request-timeout-seconds=1",
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminMlJobApiIntegrationTest extends IntegrationTest {

    private static final String SCANNER = "admin-ml-scanner@example.com";
    private static final String OPERATOR = "admin-ml-operator@example.com";
    private static final String ORDINARY_USER = "admin-ml-ordinary@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MlJobRepository mlJobRepository;

    private CookpalUser scanner;
    private MlJob runningJob;
    private MlJob failedJob;

    @BeforeEach
    void setup() {
        mlJobRepository.deleteAll();
        scanner = userNamed(SCANNER);

        runningJob = mlJobRepository.save(MlJob.builder()
                .id(UUID.randomUUID().toString())
                .owner(scanner)
                .jobType(MlJobService.RECIPE_OCR_JOB_TYPE)
                .status(MlJobStatus.QUEUED)
                .remoteJobId("remote-1")
                .build());

        failedJob = mlJobRepository.save(MlJob.builder()
                .id(UUID.randomUUID().toString())
                .owner(scanner)
                .jobType(MlJobService.RECIPE_OCR_JOB_TYPE)
                .status(MlJobStatus.FAILED)
                .errorCode("ML_TIMEOUT")
                .errorMessage("The subsystem did not finish this job in time")
                .build());
    }

    @Test
    void anOperatorSeesEveryScanAndWhoRanIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/jobs").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id=='" + failedJob.getId() + "')].ownerEmailAddress")
                        .value(SCANNER))
                .andExpect(jsonPath("$[?(@.id=='" + failedJob.getId() + "')].errorCode")
                        .value("ML_TIMEOUT"));
    }

    @Test
    void scansCanBeNarrowedToOnePersonAndOneState() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/jobs")
                .param("userId", scanner.getUserId().toString())
                .param("status", MlJobStatus.FAILED.name())
                .with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(failedJob.getId()));

        mockMvc.perform(get("/api/v1/admin/ml/jobs")
                .param("userId", String.valueOf(scanner.getUserId() + 10_000))
                .with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void resettingAStuckScanStopsItAndGivesTheAllowanceBack() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ml/jobs/" + runningJob.getId() + "/reset").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(MlJobStatus.CANCELLED.name()))
                .andExpect(jsonPath("$.countsTowardsQuota").value(false));

        var reset = mlJobRepository.findById(runningJob.getId()).orElseThrow();
        assertThat(reset.getFinishedAt()).isNotNull();
        assertThat(reset.isCountsTowardsQuota()).isFalse();
    }

    @Test
    void resettingAFinishedScanOnlyGivesTheAllowanceBack() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ml/jobs/" + failedJob.getId() + "/reset").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(MlJobStatus.FAILED.name()))
                .andExpect(jsonPath("$.countsTowardsQuota").value(false));
    }

    @Test
    void aScanThatIsNotThereCannotBeReset() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ml/jobs/does-not-exist/reset").with(operator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOperatorCanDeleteAScan() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/ml/jobs/" + failedJob.getId()).with(operator()))
                .andExpect(status().isNoContent());

        assertThat(mlJobRepository.findById(failedJob.getId())).isEmpty();
    }

    @Test
    void theQuotaOverviewCountsWhatWasRunToday() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/quota").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.emailAddress=='" + SCANNER + "')].used").value(2));

        mockMvc.perform(post("/api/v1/admin/ml/quota/" + scanner.getUserId() + "/reset").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.emailAddress=='" + SCANNER + "')]").isEmpty());
    }

    @Test
    void anOrdinaryUserCannotSeeOtherPeoplesScans() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ml/jobs").with(user(ORDINARY_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinaryUserCannotResetSomebodyElsesScan() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ml/jobs/" + runningJob.getId() + "/reset")
                .with(user(ORDINARY_USER)))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }

    private CookpalUser userNamed(String emailAddress) {
        var existing = userRepository.findByEmailAddress(emailAddress);
        if (existing != null) {
            return existing;
        }
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setPasswordHash("irrelevant");
        user.setActivated(true);
        return userRepository.save(user);
    }
}
