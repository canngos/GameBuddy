// Reads the verification code that was just issued for PENDING_EMAIL.
//
// Runs on the host inside Maestro's JS sandbox, which can make HTTP calls but cannot reach
// Postgres — so it asks code-server.js, which can. See code-server.js for why the code has
// to be read here rather than passed in as a variable.

var email = PENDING_EMAIL;
var res = http.get('http://127.0.0.1:8099/code?email=' + encodeURIComponent(email));

if (!res.ok) {
  throw new Error('code-server said ' + res.status + ': ' + res.body +
    ' (is it running? node qa/maestro/code-server.js)');
}

output.code = json(res.body).code;
