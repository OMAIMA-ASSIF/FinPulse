<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('otp') displayInfo=false; section>
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
    #kc-register-otp-form {
        display: flex;
        flex-direction: column;
        gap: 30px;
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
    .login-div{
        width:650px;
        background:linear-gradient(-90deg, #accbee 0%, #e7f0fd 100%);
        border-radius: 20px;
        margin-right: 110px;
    }
    .logo img {
        height: 50px;
        width: 200px;
    }
    .otp-page {
        width: 400px;
        margin-top: 40px;
        display: flex;
        flex-direction: column;
        gap: 50px;
    }
    .form-title {
        font-size: 28px;
        font-weight: 600;
        color: #181D25;
    }
    .form-subtitle {
        font-size: 14px;
    }
    .form-subtitle strong {
        font-weight: 600;
    }
    .form-control {
        width: 370px;
        padding: 12px 14px;
        border: 1px solid #ccc;
        border-radius: 8px;
        font-size: 16px;
    }
    .alert-error.pf-m-danger{
        color: red;
        font-size: 20px;
        margin-top: 8px;
    }
    .btn-primary {
        background: #0033cc;
        color: #fff;
        padding: 14px;
        border: none;
        border-radius: 8px;
        width: 100%;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: 0.3s;
    }
    .btn-primary:hover {
        background: #001f80;
    }
    .resend-section {
        display: flex;
        align-items: center;
        gap: 10px;
        font-size: 13px;
    }
    a {
        font-size: 15px;
        color: #4E5562;
        font-weight: 550;
    }
    a:hover {
        color: rgb(221, 118, 34);
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
    <div class="login-left">
        <div class="logo">
            <img src="${url.resourcesPath}/img/logo.png" alt="FasoRanana Logo"/>
        </div>
        <div class="otp-page">
            <div class="form-title">Vérifiez votre compte</div>
            <div class="form-subtitle">
                Un code de vérification a été envoyé pour confirmer votre inscription.
    
            </div>

            <form id="kc-register-otp-form" action="${url.registrationAction}" method="post">
                <input id="otp" name="otp" autocomplete="off" type="text" class="form-control" 
                       maxlength="6" pattern="[0-9]{6}"
                       placeholder="Code OTP (6 chiffres)" autofocus/>
                <#if messagesPerField.existsError('otp')>
                    <span style="color:red;font-size:13px;">
                        ${kcSanitize(messagesPerField.getFirstError('otp'))?no_esc}
                    </span>
                </#if>

                <div class="resend-section">
                    <p>Vous n’avez pas reçu le code ?</p>
                    <button type="button" id="resend-otp-btn" class="btn-primary" style="width:auto; padding:8px 12px; font-size:13px;">Renvoyer le code </button>
                    <span id="resend-timer"></span>
                </div>

                <input class="btn-primary" name="verify" id="kc-verify" type="submit" value="Vérifier"/>

                <div style="margin-top:10px;">
                    <a href="${url.registrationUrl}" class="lien">Retour à l’inscription</a>
                </div>
            </form>
        </div>
    </div>
    <div class="login-right">
        <div class="login-div">
            <img src="${url.resourcesPath}/img/bg-side.png" alt="Femme heureuse avec un téléphone">
        </div>
    </div>
</div>

<script>
    let resendTimer = 60;
    let timerInterval;

    function startResendTimer() {
        const resendBtn = document.getElementById('resend-otp-btn');
        const timerSpan = document.getElementById('resend-timer');
        
        resendBtn.disabled = true;
        resendBtn.textContent = '${msg("resendOtp")} (' + resendTimer + 's)';
        
        timerInterval = setInterval(function() {
            resendTimer--;
            if (resendTimer > 0) {
                resendBtn.textContent = '${msg("resendOtp")} (' + resendTimer + 's)';
            } else {
                clearInterval(timerInterval);
                resendBtn.disabled = false;
                resendBtn.textContent = '${msg("resendOtp")}';
                resendTimer = 60;
            }
        }, 1000);
    }
    startResendTimer();

    document.getElementById('resend-otp-btn').addEventListener('click', function() {
        fetch('${url.registrationAction}', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: 'resend-otp=true'
        }).then(response => {
            if (response.ok) {
                alert('${msg("otpResent")}');
                startResendTimer();
            }
        }).catch(error => {
            console.error('Erreur lors du renvoi de l\'OTP:', error);
        });
    });

    const otpInput = document.getElementById('otp');
    otpInput.addEventListener('input', function(e) {
        e.target.value = e.target.value.replace(/[^0-9]/g, '');
        if (e.target.value.length === 6) {
            document.getElementById('kc-register-otp-form').submit();
        }
    });
</script>

</#if>
</@layout.registrationLayout>
