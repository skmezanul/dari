package com.psddev.dari.elasticsearch;

import org.elasticsearch.client.transport.TransportClient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class ElasticDatabaseConnectionTest {

    private static final int ELASTICPORT = 9300;
    private static final int ELASTICRESTPORT = 9200;

    @Test
    public void testMultipleConnections() {
        String nodeHost = "http://localhost:" + ELASTICRESTPORT + "/";
        String elasticCluster = ElasticsearchDatabase.getClusterName(nodeHost);

        org.elasticsearch.common.settings.Settings nodeSettings;
        List<ElasticsearchNode> nodes = new ArrayList<>();

        nodeSettings = org.elasticsearch.common.settings.Settings.builder()
                .put("cluster.name", elasticCluster)
                .put("client.transport.sniff", true).build();

        ElasticsearchNode n = new ElasticsearchNode();
        n.setPort(ELASTICPORT);
        n.setRestPort(ELASTICRESTPORT);
        n.setHostname("localhost");
        nodes.add(n);
        TransportClient c = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        TransportClient c1 = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        TransportClient c2 = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        assertThat(c.hashCode(), is(c1.hashCode()));
        assertThat(c1.hashCode(), is(c2.hashCode()));

        nodeSettings = org.elasticsearch.common.settings.Settings.builder()
                .put("cluster.name", "newcluster")
                .put("client.transport.sniff", true).build();

        TransportClient newConn = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        assertThat(c.hashCode(), is(not(newConn.hashCode())));
        ElasticsearchDatabaseConnection.closeClients();
    }
}
