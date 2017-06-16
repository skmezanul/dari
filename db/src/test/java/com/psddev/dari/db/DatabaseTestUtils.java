package com.psddev.dari.db;

import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.UuidUtils;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseTestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTestUtils.class);

    public static List<TestDatabase> getNewDefaultTestDatabaseInstances() {
        List<TestDatabase> testDbs = new ArrayList<TestDatabase>();

        String classesString = Settings.get(String.class, "dari/testDatabaseClasses");
        if (!ObjectUtils.isBlank(classesString)) {
            String[] parts = classesString.split(",");
            for (String part : parts) {
                try {
                    Class<?> klass = Class.forName(part.trim());
                    TestDatabase testDb = getTestDatabaseForImplementation(klass);
                    if (testDb != null) {
                        testDbs.add(testDb);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return testDbs;
    }

    public static TestDatabase getTestDatabaseForImplementation(Class<?> klass) {
        if (klass.equals(SqlDatabase.class)) {
            return getSqlTestDatabase();
        } else if (klass.equals(SolrDatabase.class)) {
            //return getSolrTestDatabase();
            return getSolrLocalTestDatabase();
        } else {
            return null;
        }
    }

    public static TestDatabase getSqlTestDatabase() {

        String dbName = UuidUtils.createSequentialUuid().toString().replaceAll("-", "");

        Map<String, Object> settings = new HashMap<String, Object>();
        settings.put(SqlDatabase.JDBC_URL_SETTING, "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");

        final SqlDatabase sqlDb = new SqlDatabase();
        sqlDb.setName("JUnit Test SQL DB " + dbName);
        sqlDb.doInitialize(null, settings);

        return new TestDatabase() {
            @Override
            public Database get() {
                return sqlDb;
            }

            @Override
            public void close() {
                sqlDb.close();
            }
        };
    }

    public static TestDatabase getSolrLocalTestDatabase() {

        String dbName = UuidUtils.createSequentialUuid().toString().replaceAll("-", "");

        Map<String, Object> settings = new HashMap<String, Object>();

        Settings.setOverride("dari/defaultDatabase", "solr");
        String prefix = "dari/database/solr";
        Settings.setOverride(prefix + "/class", SolrDatabase.class.getName());
        Settings.setOverride(prefix + "/" + SolrDatabase.SERVER_URL_SUB_SETTING, "http://localhost:8080/solr/collection1");
        Settings.setOverride(prefix + "/" + SolrDatabase.READ_SERVER_URL_SUB_SETTING, "http://localhost:8080/solr/collection1");
        Settings.setOverride(prefix + "/" + SolrDatabase.VERSION_SUB_SETTING, "4.8.1");

        settings.put(SolrDatabase.SERVER_URL_SUB_SETTING, "http://localhost:8080/solr/collection1");
        settings.put(SolrDatabase.READ_SERVER_URL_SUB_SETTING, "http://localhost:8080/solr/collection1");
        settings.put(SolrDatabase.VERSION_SUB_SETTING, "4.8.1");

        final SolrDatabase solrDb = new SolrDatabase();
        solrDb.setName("JUnit Test Solr DB " + dbName);
        solrDb.doInitialize(null, settings);
        solrDb.deleteByQuery(Query.fromAll());
        solrDb.commit();

        return new TestDatabase() {
            @Override
            public Database get() {
                return solrDb;
            }

            @Override
            public void close() {

            }
        };
    }

    public static TestDatabase getSolrTestDatabase() {

        String path = null;
        String dbName = "solr_" + UuidUtils.createSequentialUuid().toString().replaceAll("-", "");
        final File solrHome = new File(System.getProperty("java.io.tmpdir"), dbName);
        try {
            path = solrHome.getCanonicalPath();
            System.setProperty("solr.solr.home", path);
            LOGGER.info("Setting Solr Home to: " + path);
        } catch (IOException e1) {
            e1.printStackTrace();
            return null;
        }

        Settings.setOverride("dbName", dbName);
        Settings.setOverride("solrHome", path);

        CoreContainer coreContainer = new CoreContainer(path);
        coreContainer.load();
        Map<String,Exception> coreInitFailures = coreContainer.getCoreInitFailures();
        if(coreInitFailures != null) {
            Set<String> keys = coreInitFailures.keySet();
            for (String key: keys) {
                Exception e = coreInitFailures.get(key);
                e.printStackTrace();
            }
        }

        if (coreContainer == null) {
            return null;
        }
        final EmbeddedSolrServer server = new EmbeddedSolrServer(coreContainer, "");

        final SolrDatabase solrDb = new SolrDatabase();
        solrDb.setName("JUnit Test Solr DB " + dbName);
        solrDb.setServer(server);
        solrDb.setVersion("4.8.1");
        solrDb.deleteByQuery(Query.fromAll());
        solrDb.commit();

        return new TestDatabase() {
            @Override
            public Database get() {
                return solrDb;
            }

            @Override
            public void close() {
                solrDb.closeConnection(server);
                removeDirectory(solrHome);
            }

            private boolean removeDirectory(File directory) {
                if (directory == null) {
                    return false;
                }
                if (!directory.exists()) {
                    return true;
                }
                if (!directory.isDirectory()) {
                    return false;
                }

                LOGGER.info("Removing directory: " + directory.getAbsolutePath());

                String[] list = directory.list();
                if (list != null) for (int i = 0; i < list.length; i++) {
                    File entry = new File(directory, list[i]);

                    if (entry.isDirectory()) {
                        if (!removeDirectory(entry)) {
                            return false;
                        }
                    } else {
                        if (!entry.delete()) {
                            return false;
                        }
                    }
                }

                return directory.delete();
            }
        };
    }
}
