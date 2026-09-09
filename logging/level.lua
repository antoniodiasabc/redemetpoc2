-- extrai nível INFO/WARN/ERROR do log Spring Boot
function extract_level(tag, timestamp, record)
    local msg = record["log"] or record["message"] or ""
    local level = "INFO"
    if string.find(msg, "%[ERROR%]") or string.find(msg, "ERROR") then level = "ERROR"
    elseif string.find(msg, "%[WARN%]")  or string.find(msg, "WARN")  then level = "WARN"
    end
    record["level"] = level
    return 1, timestamp, record
end
