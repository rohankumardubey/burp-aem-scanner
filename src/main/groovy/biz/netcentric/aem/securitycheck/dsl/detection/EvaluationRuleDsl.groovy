package biz.netcentric.aem.securitycheck.dsl.detection

import biz.netcentric.aem.securitycheck.http.Cookie
import biz.netcentric.aem.securitycheck.http.ResponseEntity
import biz.netcentric.aem.securitycheck.model.EvaluationResult
import org.apache.commons.lang3.StringUtils

/**
 * Evaluates an expectation based on the syntax.
 *  expectkeyword attribute operator 'expected value'
 *
 * Any expectation starts with the keyword expect, followed by the attribute to evaluate.
 * Checking for cookies has a special syntax as cookies are not simple string value
 * See the following example
 * {@code
 *  expect body contains "sometoken"
 *  cookie name: "someName" property "domain" equals "github.com"
 *  cookie name: "someOtherName" with "domain" contains "adobe"
 * }
 *
 * Requires the response to be set through the constructor.
 *
 * Supported operators:
 * contains, equals and is
 */
class EvaluationRuleDsl {

    ResponseEntity responseEntity

    List<EvaluationResult> result = []

    List<String> attributeValues = []

    EvaluationRuleDsl(ResponseEntity responseEntity) {
        this.responseEntity = responseEntity
    }

    def body = {
        // wrap in list as we may have different eval criteria to check
        return [responseEntity.getMessageBody()]
    }

    def mimeType = {
        // wrap in list as we may have different eval criteria to check
        return [responseEntity.getMimeType()]
    }

    def status = {
        return ["${responseEntity.statusCode}"]
    }

    def headers = {
        return responseEntity.getHeaders()
    }

    def cookies = {
        return responseEntity.getCookies()
    }

    def cookie = {criteria ->
        // the argument is a map then we add all criteria if not then we treat it as a String
        Map criteriaProperties = [:]
        if(criteria instanceof Map){
            criteriaProperties.putAll(criteria)
        }else{
            criteriaProperties.put("name", criteria)
        }

        return cookieByName(criteriaProperties)
    }

    EvaluationRuleDsl expect(Closure responseAttribute) {
        this.attributeValues.addAll(responseAttribute(responseEntity))
        this
    }

    CookieDsl cookie(Map m) {
        String name = Optional.of(m.get("name")).orElse("")
        Cookie selectedCookie = responseEntity.getCookies()
                .stream()
                .filter(cookie -> {
                    return StringUtils.equalsIgnoreCase(name, cookie.name())
                })
                .findFirst()
                .orElse(null)

        new CookieDsl(parent: this, cookie: selectedCookie)
    }

    String with(String property, Object value){

    }

    void contains(String... tokens) {
        int matches = 0
        attributeValues.each { attributeValue ->
            if (isStringAttribute(attributeValue) && StringUtils.containsAny(attributeValue, tokens)) {
                matches++;
            }
        }

        evaluateResults("contains ${tokens.toList()}", matches)
    }

    private boolean isStringAttribute(String attributeValue) {
        attributeValue != null && attributeValue instanceof String
    }

    void matches(String... patterns) {
        int matches = 0
        attributeValues.each { attributeValue ->
            if (isStringAttribute(attributeValue)) {
                patterns.each { pattern ->
                    if (attributeValue =~ pattern) {
                        matches++
                    }
                }
            }
        }

        evaluateResults("matches ${patterns.toList()}", matches)
    }

    void equals(String... tokens) {
        int matches = 0
        attributeValues.each { attributeValue ->
            if (isStringAttribute(attributeValue) && StringUtils.equalsAny(attributeValue, tokens)) {
                matches++
            }
        }

        evaluateResults("equals ${tokens.toList()}", matches)
    }

    void is(Object token) {
        int matches = 0
        attributeValues.each { attributeValue ->
            if (isStringAttribute(attributeValue) && StringUtils.equals(attributeValue, String.valueOf(token))) {
                matches++
            }
        }

        evaluateResults("is ${token}", matches)
    }

    private void evaluateResults(String condition, int matches) {
        boolean result = matches > 0
        this.result.add(EvaluationResult.builder()
                .name(condition)
                .isMatch(result)
                .build())
    }
}
