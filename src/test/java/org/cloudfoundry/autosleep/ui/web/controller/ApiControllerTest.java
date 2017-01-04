package org.cloudfoundry.autosleep.ui.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.config.Config.CloudFoundryAppState;
import org.cloudfoundry.autosleep.dao.model.Binding;
import org.cloudfoundry.autosleep.dao.model.ApplicationInfo;
import org.cloudfoundry.autosleep.dao.model.Binding.ResourceType;
import org.cloudfoundry.autosleep.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.dao.repositories.ApplicationRepository;
import org.cloudfoundry.autosleep.dao.repositories.BindingRepository;
import org.cloudfoundry.autosleep.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.ui.web.model.ServerResponse;
import org.cloudfoundry.autosleep.util.ApplicationLocker;
import org.cloudfoundry.autosleep.util.BeanGenerator;
import org.cloudfoundry.community.servicebroker.model.Catalog;
import org.cloudfoundry.community.servicebroker.model.Plan;
import org.cloudfoundry.community.servicebroker.model.ServiceDefinition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class ApiControllerTest {

    private static final String applicationId = UUID.randomUUID().toString();

    private static final String serviceInstanceId = "id";

    private static final String serviceBindingId = "serviceBindingId";

    @Mock
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Mock
    private BindingRepository bindingRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private Catalog catalog;

    @Mock
    private ApplicationLocker applicationLocker;


    @InjectMocks
    private ApiController apiController;


    private MockMvc mockMvc;

    private ObjectMapper objectMapper;


    @Before
    public void init() {
        objectMapper = new ObjectMapper();
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArguments()[1]).run();
            return null;
        }).when(applicationLocker).executeThreadSafe(anyString(), any(Runnable.class));
        mockMvc = MockMvcBuilders.standaloneSetup(apiController).build();
        when(catalog.getServiceDefinitions()).thenReturn(Collections.singletonList(
                new ServiceDefinition("serviceDefinitionId", "serviceDefinition", "", true,
                        Collections.singletonList(new Plan("planId", "plan", "")))));
    }


    @Test
    public void testListInstances() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(Config.ServiceInstanceParameters.IDLE_DURATION, Duration.ofMinutes(15));
        parameters.put(Config.ServiceInstanceParameters.EXCLUDE_FROM_AUTO_ENROLLMENT, Pattern.compile(".*"));

        SpaceEnrollerConfig serviceInstance = SpaceEnrollerConfig.builder()
                .id(serviceInstanceId).build();

        when(spaceEnrollerConfigRepository.findAll()).thenReturn(Collections.singletonList(serviceInstance));


        mockMvc.perform(get(Config.Path.API_CONTEXT + Config.Path.SERVICES_SUB_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content()
                .contentType(new MediaType(MediaType.APPLICATION_JSON,
                        Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    verify(spaceEnrollerConfigRepository, times(1)).findAll();
                    ServerResponse<SpaceEnrollerConfig[]> serviceInstances = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(),
                                    TypeFactory.defaultInstance()
                                            .constructParametricType(ServerResponse.class,
                                                    SpaceEnrollerConfig[].class));
                    assertThat(serviceInstances.getBody(), is(notNullValue()));
                    assertThat(serviceInstances.getBody().length, is(equalTo(1)));
                    assertThat(serviceInstances.getBody()[0].getId(),
                            is(equalTo(serviceInstanceId)));

                });
    }

    @Test
    public void testListBindings() throws Exception {
        Binding serviceBinding = Binding.builder()
                .serviceBindingId(serviceBindingId)
                .serviceInstanceId(serviceInstanceId)
                .resourceId(UUID.randomUUID().toString())
                .resourceType(ResourceType.Application)
                .build();
        when(bindingRepository.findAll()).thenReturn(Collections.singletonList(serviceBinding));

        mockMvc.perform(get(Config.Path.API_CONTEXT + Config.Path.SERVICES_SUB_PATH + serviceInstanceId + "/bindings/")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content()
                .contentType(new MediaType(MediaType.APPLICATION_JSON,
                        Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    verify(bindingRepository, times(1)).findAll();
                    ServerResponse<Binding[]> serviceBindings = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(),
                                    TypeFactory.defaultInstance()
                                            .constructParametricType(ServerResponse.class,
                                                    Binding[].class));
                    assertThat(serviceBindings.getBody(), is(notNullValue()));
                    assertThat(serviceBindings.getBody().length, is(equalTo(1)));
                    assertThat(serviceBindings.getBody()[0].getServiceBindingId(), is(equalTo(serviceBindingId)));


                });
        mockMvc.perform(get(Config.Path.API_CONTEXT + Config.Path.SERVICES_SUB_PATH + serviceInstanceId + "-tmp"
                + "/bindings/")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content()
                .contentType(new MediaType(MediaType.APPLICATION_JSON,
                        Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    verify(bindingRepository, times(2)).findAll();
                    ServerResponse<Binding[]> serviceBindings = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(),
                                    TypeFactory.defaultInstance()
                                            .constructParametricType(ServerResponse.class,
                                                    Binding[].class));
                    assertThat(serviceBindings.getBody(), is(notNullValue()));
                    assertThat(serviceBindings.getBody().length, is(equalTo(0)));
                });
    }

    @Test
    public void testListApplications() throws Exception {

        ApplicationInfo applicationInfo = BeanGenerator.createAppInfoWithDiagnostic(applicationId, "applicationName",
                CloudFoundryAppState.STARTED);

        applicationInfo.getEnrollmentState().addEnrollmentState("serviceId");
        when(applicationRepository.findAll()).thenReturn(Collections.singletonList(applicationInfo));

        mockMvc.perform(get(Config.Path.API_CONTEXT + Config.Path.APPLICATIONS_SUB_PATH)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(new MediaType(MediaType.APPLICATION_JSON,
                                Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    verify(applicationRepository, times(1)).findAll();
                    ServerResponse<ApplicationInfo[]> applicationInfos = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(),
                                    TypeFactory.defaultInstance()
                                            .constructParametricType(ServerResponse.class,
                                                    ApplicationInfo[].class));
                    assertThat(applicationInfos.getBody(), is(notNullValue()));
                    assertThat(applicationInfos.getBody().length, is(equalTo(1)));
                    assertThat(applicationInfos.getBody()[0].getUuid(), is(equalTo(applicationId.toString())));
                });
    }

    @Test
    public void testDeleteApplication() throws Exception {
        String applicationId = "applicationToDelete";
        mockMvc.perform(delete(Config.Path.API_CONTEXT + Config.Path.APPLICATIONS_SUB_PATH + applicationId))
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()))
                .andDo(mvcResult -> verify(applicationRepository, times(1)).delete(eq(applicationId)));


    }

    @Test
    public void testListApplicationById() throws Exception {
        String serviceId = "serviceIdListById";
        ApplicationInfo applicationInfo = BeanGenerator.createAppInfoWithDiagnostic(applicationId.toString(),
                "appName", CloudFoundryAppState.STARTED);
        applicationInfo.getEnrollmentState().addEnrollmentState(serviceId);
        when(applicationRepository.findAll()).thenReturn(Collections.singletonList(applicationInfo));

        mockMvc.perform(get(Config.Path.API_CONTEXT + Config.Path.SERVICES_SUB_PATH + serviceId + "/applications/")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(new MediaType(MediaType.APPLICATION_JSON,
                                Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    verify(applicationRepository, times(1)).findAll();
                    ServerResponse<ApplicationInfo[]> applicationInfos = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(),
                                    TypeFactory.defaultInstance()
                                            .constructParametricType(ServerResponse.class,
                                                    ApplicationInfo[].class));
                    assertThat(applicationInfos.getBody(), is(notNullValue()));
                    assertThat(applicationInfos.getBody().length, is(equalTo(1)));
                    assertThat(applicationInfos.getBody()[0].getUuid(), is(equalTo(applicationId.toString())));
                });
    }


}