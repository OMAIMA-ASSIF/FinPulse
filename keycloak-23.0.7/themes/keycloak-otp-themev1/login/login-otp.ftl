
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError("totp") displayInfo=false; section>

<#if section = "header">
    <title>Vérification par code - FasoRanana</title>
<#elseif section = "form">
<style>
    html, body {
        margin: 0;
        padding: 0;
        font-family: Inter, sans-serif;
        background: #f5f7fa;
        color: #4E5562;
        height: 100%;
        overflow: hidden;
        font-size: 0px;
    }
    .login-container {
        display: flex;
        min-height: 100vh;
        width: 100%;
        box-sizing: border-box;
    }
    .login-left {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        padding: 60px;
        padding-left: 110px;
        background: #fff;
        position: relative;
    }
    .login-page {
        width: 640px;
        margin-top: 100px;
        height: auto;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
    .login-right {
        flex: 1;
        background: white;
        display: flex;
        align-items: center;
        justify-content: center;
        max-width: 100%;
    }
    .login-right img {
        max-width: 95%;
    }
    .login-div {
        width: 650px;
        background: linear-gradient(-90deg, #accbee 0%, #e7f0fd 100%);
        border-radius: 20px;
        margin-right: 150px;
    }
    .logo {
        margin-bottom: 40px;
        margin-top: 0;
        padding-top: 0;
    }
    .logo img {
        height: 50px;
        display: block;
        margin-top: -20px;
        width: 200px;
    }
    .form-title {
        font-size: 28px;
        font-weight: 600;
        color: #181D25;
        margin-bottom: 10px;
    }
    .form-subtitle {
        margin-bottom: 30px;
        font-size: 14px;
    }
    /* Nouveau style pour le champ OTP unique */
    #otp {
        width: 370px;
        padding: 12px 14px;
        border: 1px solid #ccc;
        border-radius: 8px;
        font-size: 20px;
        text-align: center;
        outline: none;
        transition: border 0.3s;
    }
    #otp:focus {
        border-color: #0033cc;
    }
    #kc-otp-login-form{
        display: flex;
        flex-direction: column;
        gap: 15px;
        }
    .resend-link {
        cursor: pointer;
        margin-top: 7px;
        margin-bottom: 7px;
        margin-left: 130px;
        display: block;
        font-size: 15px;
        text-decoration: none;
    }
    a:hover {
        color: rgb(221, 118, 34)!important;
    }
    a {
        color: #4E5562!important;
        font-weight: 600 !important;
    }
    .btn-primary {
        background: #0033cc;
        color: #fff;
        padding: 14px;
        border: none;
        border-radius: 8px;
        width: 400px;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: 0.3s;
    }
    .btn-primary:hover {
        background: #001f80;
    }
    footer {
        margin-top: 100px;

        font-size: 13px;
        color: #4E5562;
    }
    .alert-error {
        background: #ffe0e0;
        border: 1px solid #ffcccc;
        padding: 12px;
        border-radius: 6px;
        color: #b30000;
        font-size: 14px;
        margin-bottom: 15px;
    }
    .futurion {
        color: #4E5562;
        font-weight: 700;
        text-decoration: none;
    }
    @media(max-width: 768px) {
        .login-container {
            flex-direction: column;
        }
        .login-right {
            display: none;
        }
        .login-left {
            padding: 40px 20px;
        }
    }
</style>

<div class="login-container">
    <!-- Left Panel -->
    <div class="login-left">
        <div class="logo">
            <img src="${url.resourcesPath}/img/logo.png" alt="FasoRanana Logo" />
        </div>
        <div class="login-page">
            <div class="form-title" style="display: flex; align-items: center; gap: 10px;">
                <span>Entrez votre code de vérification</span>
                <img src="https://static.vecteezy.com/system/resources/previews/014/571/683/non_2x/yellow-padlock-for-locking-the-information-on-the-computer-data-encryption-concept-png.png" alt="Padlock" style="width: 50px; height: auto;">
            </div>

            <div class="form-subtitle">Un code de vérification a été envoyé à votre adresse e-mail ou à votre téléphone.</div>

            <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
                <input id="otp" name="otp" autocomplete="off" type="text" maxlength="6" pattern="[0-9]{6}" placeholder="Code OTP (6 chiffres)" autofocus />
                <#if messagesPerField.existsError("totp")>
                    <div class="alert-error">
                        ${kcSanitize(messagesPerField.getFirstError("totp"))?no_esc}
                    </div>
                </#if>

                <div >
                    <a class="resend-link" id="resend-otp-btn">Renvoyer le code</a>
                </div>

                <div class="form-group">
                    <button type="submit" class="btn-primary">Se connecter</button>
                </div>
            </form>
        </div>

        <footer>
            <div class="help-link">
                <a href="#">Besoin d'aide ?</a>
            </div>
            <p>© Tous droits réservés. Fabriqué par <a href="https://futurion.tech" target="_blank" class="futurion">Futurion</a></p>
        </footer>
    </div>

    <!-- Right Panel -->
    <div class="login-right">
        <div class="login-div">
            <img src="${url.resourcesPath}/img/bg-side.png" alt="Illustration">
        </div>
    </div>
</div>

<script>
    document.title = "Vérification par code - FasoRanana";

    const otpInput = document.getElementById('otp');

    // Autoriser uniquement les chiffres dans l'input OTP
    otpInput.addEventListener('input', (e) => {
        e.target.value = e.target.value.replace(/[^0-9]/g, '');
    });

    // Action sur clic du bouton renvoyer le code
    document.getElementById("resend-otp-btn").addEventListener("click", function(e) {
        e.preventDefault();
        fetch("${url.loginAction}", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: "resend-otp=true"
        }).then(response => {
            if (response.ok) {
                alert("${msg("otpResent")}");
            }
        });
    });
</script>
</#if>
</@layout.registrationLayout>
