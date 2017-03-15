package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Recordable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UuidElasticIndexModel extends AbstractElasticIndexModel<UuidElasticIndexModel, UUID> {

    @Recordable.Indexed
    private UUID one;

    @Recordable.Indexed
    private Set<UUID> set;

    @Recordable.Indexed
    private List<UUID> list;

    @Recordable.Indexed
    private UuidElasticIndexModel referenceOne;

    @Recordable.Indexed
    private Set<UuidElasticIndexModel> referenceSet;

    @Recordable.Indexed
    private List<UuidElasticIndexModel> referenceList;

    @Recordable.Embedded
    @Recordable.Indexed
    private UuidElasticIndexModel embeddedOne;

    @Recordable.Embedded
    @Recordable.Indexed
    private Set<UuidElasticIndexModel> embeddedSet;

    @Recordable.Embedded
    @Recordable.Indexed
    private List<UuidElasticIndexModel> embeddedList;

    @Override
    public UUID getOne() {
        return one;
    }

    @Override
    public void setOne(UUID one) {
        this.one = one;
    }

    @Override
    public Set<UUID> getSet() {
        if (set == null) {
            set = new LinkedHashSet<>();
        }
        return set;
    }

    @Override
    public void setSet(Set<UUID> set) {
        this.set = set;
    }

    @Override
    public List<UUID> getList() {
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    @Override
    public void setList(List<UUID> list) {
        this.list = list;
    }

    @Override
    public UuidElasticIndexModel getReferenceOne() {
        return referenceOne;
    }

    @Override
    public void setReferenceOne(UuidElasticIndexModel referenceOne) {
        this.referenceOne = referenceOne;
    }

    @Override
    public Set<UuidElasticIndexModel> getReferenceSet() {
        if (referenceSet == null) {
            referenceSet = new LinkedHashSet<>();
        }
        return referenceSet;
    }

    @Override
    public void setReferenceSet(Set<UuidElasticIndexModel> referenceSet) {
        this.referenceSet = referenceSet;
    }

    @Override
    public List<UuidElasticIndexModel> getReferenceList() {
        if (referenceList == null) {
            referenceList = new ArrayList<>();
        }
        return referenceList;
    }

    @Override
    public void setReferenceList(List<UuidElasticIndexModel> referenceList) {
        this.referenceList = referenceList;
    }

    @Override
    public UuidElasticIndexModel getEmbeddedOne() {
        return embeddedOne;
    }

    @Override
    public void setEmbeddedOne(UuidElasticIndexModel embeddedOne) {
        this.embeddedOne = embeddedOne;
    }

    @Override
    public Set<UuidElasticIndexModel> getEmbeddedSet() {
        if (embeddedSet == null) {
            embeddedSet = new LinkedHashSet<>();
        }
        return embeddedSet;
    }

    @Override
    public void setEmbeddedSet(Set<UuidElasticIndexModel> embeddedSet) {
        this.embeddedSet = embeddedSet;
    }

    @Override
    public List<UuidElasticIndexModel> getEmbeddedList() {
        if (embeddedList == null) {
            embeddedList = new ArrayList<>();
        }
        return embeddedList;
    }

    @Override
    public void setEmbeddedList(List<UuidElasticIndexModel> embeddedList) {
        this.embeddedList = embeddedList;
    }
}
