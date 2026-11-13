<#import "template.ftl" as layout>
<#-- Page d'information : quand Keycloak propose un lien unique (poursuivre l'action, ou retourner à l'application),
     on le suit automatiquement. Le lien reste affiché si le navigateur bloque la redirection. -->
<#assign suite = "">
<#if !(skipLink??)>
    <#if pageRedirectUri?has_content>
        <#assign suite = pageRedirectUri>
    <#elseif actionUri?has_content>
        <#assign suite = actionUri>
    <#elseif (client.baseUrl)?has_content>
        <#assign suite = client.baseUrl>
    </#if>
</#if>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${kcSanitize(msg("${messageHeader}"))?no_esc}
        <#else>
        ${message.summary}
        </#if>
    <#elseif section = "form">
    <div id="kc-info-message">
        <p class="instruction">${message.summary}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list><#else></#if></p>
        <#if suite?has_content>
            <#if pageRedirectUri?has_content || !actionUri?has_content>
                <p><a id="kc-suite" href="${suite}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
            <#else>
                <p><a id="kc-suite" href="${suite}">${kcSanitize(msg("proceedWithAction"))?no_esc}</a></p>
            </#if>
            <script>window.location.replace("${suite?js_string}");</script>
        </#if>
    </div>
    </#if>
</@layout.registrationLayout>
