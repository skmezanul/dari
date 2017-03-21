package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;


public class UserModel extends Record {

    @Indexed
    public String userName;

}


