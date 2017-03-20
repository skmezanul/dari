package com.psddev.dari.elasticsearch;

import com.psddev.dari.util.Settings;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public abstract class AbstractElasticTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticTest.class);
    private static boolean initialize = true;
    private static boolean isEmbedded = false;

    public static boolean getIsEmbedded() {
        return isEmbedded;
    }

    public static void setIsEmbedded(boolean isEmbedded) {
        AbstractElasticTest.isEmbedded = isEmbedded;
    }

    /**
     *
     */
    public static void deleteIndex(String index, String nodeHost) throws IOException {
        LOGGER.info("Deleting Index " + index);
        try {
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpDelete delete = new HttpDelete(nodeHost + index);
            delete.addHeader("accept", "application/json");
            HttpResponse response = httpClient.execute(delete);
            EntityUtils.toString(response.getEntity());
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Warning: deleteIndex[%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
            throw error;
        }
    }

    /**
     * Create the index and map it
     */
    public static void createIndexandMapping(String index, String nodeHost) throws IOException {
        LOGGER.info("Mapping Index " + index);
        try {
            String jsonMap = ElasticsearchDatabase.getMapping("");
            String jsonSetting = ElasticsearchDatabase.getSetting("");
            String json = "{\n"
                    + "  \"settings\":\n"
                    + jsonSetting + ",\n"
                    + "  \"mappings\": {\n"
                    + "    \"_default_\":\n" +
                    jsonMap + "}}";

            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPut put = new HttpPut(nodeHost + index);
            put.addHeader("accept", "application/json");
            StringEntity input = new StringEntity(json);
            put.setEntity(input);
            HttpResponse response = httpClient.execute(put);
            if (response.getStatusLine().getStatusCode() > 201) {
                LOGGER.info("ELK createIndexandMapping Response > 201");
                assertThat("Response > 201", response.getStatusLine().getStatusCode(), greaterThan(201));
            }
            json = EntityUtils.toString(response.getEntity());
            LOGGER.info("ELK createIndexandMapping Response " + json);
        } catch (ClientProtocolException error) {
            LOGGER.warn(
                    String.format("Warning: createIndexandMapping[%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
            throw error;
        } catch (IOException error) {
            LOGGER.warn(
                    String.format("Warning: createIndexandMapping[%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
            throw error;
        }
    }

    /**
     * Create Elastic database if !initialize
     */
    @BeforeClass
    public static void createDatabase() throws IOException {
        if (initialize) {
            initialize = false;

            String clusterName = "elasticsearch_a";

            Settings.setOverride(ElasticsearchDatabase.DEFAULT_DATABASE_NAME, ElasticsearchDatabase.DATABASE_NAME);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "class", ElasticsearchDatabase.class.getName());
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, clusterName);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING, "index1");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "9300");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "1/" + ElasticsearchDatabase.HOSTNAME_SUB_SETTING, "localhost");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.SUBQUERY_RESOLVE_LIMIT_SETTING, "1000");

            String nodeHost = ElasticsearchDatabase.getNodeHost();
            if (!ElasticsearchDatabase.checkAlive(nodeHost)) {
                // ok create embedded since it is not already running for test
                isEmbedded = true;
                EmbeddedElasticsearchServer.setup(clusterName);
            }
            String verifyClusterName = ElasticsearchDatabase.getClusterName(nodeHost);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, verifyClusterName);
            deleteIndex((String) Settings.get(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING), nodeHost);
            deleteIndex(Settings.get(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING) + "*", nodeHost);
            // create base index1
            createIndexandMapping((String) Settings.get(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING), nodeHost);
        }
    }
}
