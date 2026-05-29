<#import "template.ftl" as layout>

<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("configureTotp")}
    <#elseif section = "form">
        <div class="kc-form-card">
            <div class="kc-form-wrapper">
                <div class="kc-form-header">
                    <h2>${msg("configureTotp")}</h2>
                </div>

                <div class="kc-feedback-text">
                    <p class="instruction">${msg("totpInstructions")}</p>
                    <p class="instruction">${msg("totpScanQrCode")}</p>
                </div>

                <div class="kc-totp-setup">
                    <#-- Vérification si qrCode existe avant de l'afficher -->
                    <#if qrCode??>
                        <div class="kc-totp-qr-code">
                            <img src="data:image/png;base64,${qrCode}" alt="${msg("totpQrCode")}" />
                        </div>
                    <#else>
                        <p class="instruction">${msg("totpQrCodeMissing")}</p>
                    </#if>

                    <#-- Vérification si totpSecret existe avant de l'afficher -->
                    <#if totpSecret??>
                        <div class="kc-totp-secret">
                            <p>${msg("totpSecret")}: <strong>${totpSecret}</strong></p>
                            <p class="instruction">${msg("totpManualEntry")}</p>
                        </div>
                    <#else>
                        <p class="instruction">${msg("totpSecretMissing")}</p>
                    </#if>
                </div>

                <form id="kc-totp-form" class="${properties.kcFormClass!}" action="${url.action}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="totp" class="${properties.kcLabelClass!}">${msg("totpCode")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input type="text" id="totp" name="totp" class="${properties.kcInputClass!}" autocomplete="off"
                                   aria-invalid="<#if messagesPerField.existsError('totp')>true</#if>"
                                   autofocus />

                            <#if messagesPerField.existsError('totp')>
                                <span id="input-error-totp" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('totp'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!}">
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                   type="submit" value="${msg("verify")}" />
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
