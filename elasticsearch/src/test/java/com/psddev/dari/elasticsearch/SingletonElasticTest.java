package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Query;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class SingletonElasticTest extends AbstractElasticTest {

    @Test
    public void exists() {
        SingletonElasticModel model = Query.from(SingletonElasticModel.class).first();
        assertThat(model, notNullValue());
        assertThat(model.getName(), notNullValue());

        List<Object> fooResult = Query
                .fromAll()
                .where("com.psddev.dari.db.Singleton$Data/dari.singleton.key equalsany ?", "com.psddev.dari.elasticsearch.SingletonElasticModel")
                .selectAll();

        assertThat(fooResult, notNullValue());
        assertThat(fooResult, hasSize(1));

        List<Object> fooResult2 = Query
                .fromAll()
                .where("_id notequalsall ? and com.psddev.dari.db.Singleton$Data/dari.singleton.key equalsany ?", model.getId(), "com.psddev.dari.elasticsearch.SingletonElasticModel")
                .selectAll();

        assertThat(fooResult2, notNullValue());
        assertThat(fooResult2, hasSize(0));
    }
}
