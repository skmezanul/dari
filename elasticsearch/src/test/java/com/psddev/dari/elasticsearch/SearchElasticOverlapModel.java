package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Switch the types from SearchElasticModel
 * This looks weird on purpose - Elastic types cannot overlap
 */
public class SearchElasticOverlapModel extends Record {

    @Indexed
    public Float one;
    public Float getOne() {
        return one;
    }
    public void setOne(Float one) {
        this.one = one;
    }

    @Indexed
    public Float eid;
    public Float getEid() {
        return eid;
    }
    public void setEid(Float eid) {
        this.eid = eid;
    }

    @Indexed
    public String num;
    public String getNum() {
        return num;
    }
    public void setNum(String num) {
        this.num = num;
    }

    @Indexed
    public String f;
    public String getF() {
        return f;
    }
    public void setF(String f) {
        this.f = f;
    }

    @Indexed
    public Date post_date;
    public Date getPostDate() {
        return post_date;
    }
    public void setPostDate(Date post_date) {
        this.post_date = post_date;
    }

    @Indexed
    public String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Indexed
    public String guid;
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Indexed
    public String message;
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    @Indexed
    public final Set<Float> set = new HashSet<>();

    @Indexed
    public final List<Float> list = new ArrayList<>();

    @Indexed
    public final Map<Float, Float> map = new HashMap<>();

    @Indexed
    public SearchElasticOverlapModel reference;
    public SearchElasticOverlapModel getReference() {
        return reference;
    }
    public void setReference(SearchElasticOverlapModel reference) {
        this.reference = reference;
    }

}

