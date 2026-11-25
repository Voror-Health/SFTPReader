var api = (function ($) {

    // Casdoor password grant: POST <casdoor>/api/login/oauth/access_token
    var getToken = function(host, clientId, clientSecret, username, password) {
        return $.ajax({
            url: host.replace(/\/+$/, "") + "/api/login/oauth/access_token",
            method: "POST",
            contentType: "application/x-www-form-urlencoded",
            data: {
                grant_type: "password",
                client_id: clientId,
                client_secret: clientSecret,
                username: username,
                password: password
            }
        });
    };

    var status = function(token) {
        return $.ajax({
            url: "api/status",
            headers: { "Authorization": "Bearer " + token }
        });
    };

    return {
      getToken: getToken,
      status: status
    };

}(jQuery));

$(document).ready(function() {

    var show = function(text) {
        $('#status').text(text);
    };

    $('#gettoken').click(function() {
        api.getToken($('#authhost').val(), $('#clientid').val(), $('#clientsecret').val(),
                     $('#username').val(), $('#password').val())
            .then(function(data) {
                if (data && data.access_token) {
                    $('#token').val(data.access_token);
                    show("Token obtained");
                } else {
                    show("No token returned: " + ((data && (data.error_description || data.error)) || "unknown error"));
                }
            }, function(xhr) {
                show("Token request failed: HTTP " + xhr.status);
            });
    });

    $('#refresh').click(function() {
        api.status($('#token').val())
            .then(function(data) {
                $('#message').text("Instance " + (data.instanceName || "(none)") + ": " + data.state);
                show(JSON.stringify(data, null, 2));
            }, function(xhr) {
                show("Status request failed: HTTP " + xhr.status + (xhr.status === 401 || xhr.status === 403 ? " (check the token / roles)" : ""));
            });
    });

});
