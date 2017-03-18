package com.psddev.dari.elasticsearch;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalSettingsPreparer;
import org.elasticsearch.painless.PainlessPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EmbeddedElasticsearchServer {

    private static final String DEFAULT_DATA_DIRECTORY = "elasticsearch-data";
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedElasticsearchServer.class);
    private static Node node = null;

    /**
     *
     */
    public static synchronized void setup() {

        List plugins = new ArrayList();
        plugins.add(Netty4Plugin.class);
        plugins.add(PainlessPlugin.class);

        try {
            LOGGER.info("Setting up new Elasticsearch embedded node");
            node = new MyNode(
                    Settings.builder()
                            .put("transport.type", "netty4")
                            .put("http.type", "netty4")
                            .put("cluster.name", "elasticdari")
                            .put("http.enabled", "true")
                            .put("path.home", DEFAULT_DATA_DIRECTORY)
                            .build(),
                    plugins);

            node.start();
            node.client().admin().cluster().prepareHealth()
                    .setWaitForYellowStatus()
                    .get();
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("EmbeddedElasticsearchServer cannot create embedded node [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
    }

    /**
     *
     */
    private static class MyNode extends Node {
        public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins);
        }
    }

    /**
     *
     */
    public static Node getNode() {
        return node;
    }

    /**
     *
     */
    public static synchronized void shutdown() {
        try {
            if (node != null) {
                node.close();
            }
        } catch (Exception e) {
            LOGGER.warn("EmbeddedElasticsearchServer cannot shutdown");
        }
        deleteDataDirectory();
    }

    /**
     *
     */
    private static void deleteDataDirectory() {
        try {
            FileUtils.deleteDirectory(new File(DEFAULT_DATA_DIRECTORY));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete data directory of embedded elasticsearch server", e);
        }
    }
}
