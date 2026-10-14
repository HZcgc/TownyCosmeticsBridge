package com.townyreborn.staffconnector;

import java.util.*;

/** Salvage valid rows only from a fully rejected numeric-validation batch; never alters gameplay. */
final class TelemetryRepair {
    static boolean valid(Map<?,?> row) {
        for(String key:List.of("value","min","max")) {
            if(!(row.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||Math.abs(n.doubleValue())>1e12)return false;
        }
        return row.get("count") instanceof Number n && n.longValue()>=1 && n.longValue()<=1_000_000;
    }
    @SuppressWarnings("unchecked") static Map<String,Object> repair(Map<?,?> archive) {
        if(!"invalid_numeric_metric".equals(archive.get("validation_error"))||!(archive.get("event") instanceof Map<?,?> event)
            ||!"telemetry.batch".equals(event.get("type"))||!(event.get("samples") instanceof List<?> samples))return null;
        String original=Objects.toString(event.get("event_id"),"");if(original.isBlank())return null;
        List<Object> valid=new ArrayList<>();
        for(Object value:samples)if(value instanceof Map<?,?> row&&valid(row))valid.add(value);
        if(valid.size()==samples.size())return null;
        Map<String,Object> result=new LinkedHashMap<>((Map<String,Object>)event);
        result.put("event_id","telemetry-repair-"+GameInsights.hash(original));result.put("samples",valid);
        return result;
    }
}
