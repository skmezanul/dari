package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SearchElasticObjectModel extends Record {

    @Indexed
    public UUID token;
    public UUID getToken() {
        return token;
    }
    public void setToken(UUID token) {
        this.token = token;
    }

    @Indexed
    public Date expireTimestamp;
    public Date getExpireTimestamp() {
        return expireTimestamp;
    }
    public void setExpireTimestamp(Date neverIndexed) {
        this.expireTimestamp = expireTimestamp;
    }


}

