package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Recordable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@org.junit.Ignore
public class NumberElasticIndexModel extends AbstractElasticIndexModel<NumberElasticIndexModel, Double> {

    @Recordable.Indexed
    private Double one;

    @Recordable.Indexed
    private Set<Double> set;

    @Recordable.Indexed
    private List<Double> list;

    @Recordable.Indexed
    private NumberElasticIndexModel referenceOne;

    @Recordable.Indexed
    private Set<NumberElasticIndexModel> referenceSet;

    @Recordable.Indexed
    private List<NumberElasticIndexModel> referenceList;

    @Recordable.Embedded
    @Recordable.Indexed
    private NumberElasticIndexModel embeddedOne;

    @Recordable.Embedded
    @Recordable.Indexed
    private Set<NumberElasticIndexModel> embeddedSet;

    @Recordable.Embedded
    @Recordable.Indexed
    private List<NumberElasticIndexModel> embeddedList;

    @Override
    public Double getOne() {
        return one;
    }

    @Override
    public void setOne(Double one) {
        this.one = one;
    }

    @Override
    public Set<Double> getSet() {
        if (set == null) {
            set = new LinkedHashSet<>();
        }
        return set;
    }

    @Override
    public void setSet(Set<Double> set) {
        this.set = set;
    }

    @Override
    public List<Double> getList() {
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    @Override
    public void setList(List<Double> list) {
        this.list = list;
    }

    @Override
    public NumberElasticIndexModel getReferenceOne() {
        return referenceOne;
    }

    @Override
    public void setReferenceOne(NumberElasticIndexModel referenceOne) {
        this.referenceOne = referenceOne;
    }

    @Override
    public Set<NumberElasticIndexModel> getReferenceSet() {
        if (referenceSet == null) {
            referenceSet = new LinkedHashSet<>();
        }
        return referenceSet;
    }

    @Override
    public void setReferenceSet(Set<NumberElasticIndexModel> referenceSet) {
        this.referenceSet = referenceSet;
    }

    @Override
    public List<NumberElasticIndexModel> getReferenceList() {
        if (referenceList == null) {
            referenceList = new ArrayList<>();
        }
        return referenceList;
    }

    @Override
    public void setReferenceList(List<NumberElasticIndexModel> referenceList) {
        this.referenceList = referenceList;
    }

    @Override
    public NumberElasticIndexModel getEmbeddedOne() {
        return embeddedOne;
    }

    @Override
    public void setEmbeddedOne(NumberElasticIndexModel embeddedOne) {
        this.embeddedOne = embeddedOne;
    }

    @Override
    public Set<NumberElasticIndexModel> getEmbeddedSet() {
        if (embeddedSet == null) {
            embeddedSet = new LinkedHashSet<>();
        }
        return embeddedSet;
    }

    @Override
    public void setEmbeddedSet(Set<NumberElasticIndexModel> embeddedSet) {
        this.embeddedSet = embeddedSet;
    }

    @Override
    public List<NumberElasticIndexModel> getEmbeddedList() {
        if (embeddedList == null) {
            embeddedList = new ArrayList<>();
        }
        return embeddedList;
    }

    @Override
    public void setEmbeddedList(List<NumberElasticIndexModel> embeddedList) {
        this.embeddedList = embeddedList;
    }
}
