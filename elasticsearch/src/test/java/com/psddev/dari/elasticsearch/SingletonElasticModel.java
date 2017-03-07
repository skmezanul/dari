package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;
import com.psddev.dari.db.Singleton;

import java.util.UUID;

public class SingletonElasticModel extends Record implements Singleton {

    @Required
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected void beforeSave() {
        setName(UUID.randomUUID().toString());
    }
}
