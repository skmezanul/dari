package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;
import com.psddev.dari.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ElasticTag extends Record {
        @Indexed
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
}
