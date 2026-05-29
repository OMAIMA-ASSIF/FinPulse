<#import "template.ftl" as layout>

<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <title>Oups, une erreur est survenue</title>
    <#elseif section = "form">
        <style>
            html, body,
            .login-pf,
            .login-pf body,
            .login-pf-page,
            .pf-c-page,
            .pf-c-page__main,
            .kc-login {
                background: linear-gradient(135deg, #f9b3a9, #f48475);

                margin: 0;
                padding: 0;
                overflow: hidden;
            }

            .kc-content-wrapper-centered {
                display: flex;
                justify-content: center;
                align-items: center;
                min-height: 100vh;
            }

            .error-container {
                width: 100%;
                max-width: 400px;
                padding: 30px 20px;
                background-color: #ffffff;
                border-radius: 10px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                text-align: center;
            }

            .error-icon {
                font-size: 50px;
                color: #e74c3c;
                margin-bottom: 20px;
            }

            .error-message {
                font-size: 16px;
                color: #333333;
                margin-bottom: 30px;
                line-height: 1.5;
            }

            .back-button {
                display: inline-block;
                padding: 12px 24px;
                background-color: #3498db;
                color: white;
                border-radius: 4px;
                text-decoration: none;
                font-weight: 500;
                transition: background-color 0.2s ease;
            }

            .back-button:hover {
                background-color: #2980b9;
            }
        </style>

        <div class="kc-content-wrapper-centered">
            <div class="error-container">
                <div class="error-icon">❌</div>

                <#if message?? && message.summary?? && message.summary?has_content>
                    <div class="error-message">${kcSanitize(message.summary)?no_esc}</div>
                <#else>
                    <div class="error-message">Une erreur est survenue. Détails non disponibles.</div>
                </#if>

                <a href="${url.loginUrl}" class="back-button">
                    ⬅ Retour à la connexion
                </a>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
