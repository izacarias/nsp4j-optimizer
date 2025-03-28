var shortPeriod = 100;
var longPeriod = 3000;
var intervalMessages = setInterval(getMessage, longPeriod);
var messages = [];
var numMessages = 7;

function getMessage() {
    try {
        $.ajax
            ({
                url: "message",
                type: "GET",
                async: false,
                success: function (ans) { successConnection(ans); },
                error: function () { errorConnection(); }
            });
    }
    catch (e) {
        return 0;
    }
}

function errorConnection() {
    if (document.getElementById("message").innerText != "WARN - backend is stopped") {
        document.getElementById("message").innerText = "WARN - backend is stopped";
        document.getElementById("run_button").setAttribute("disabled", "true");
        document.getElementById("stop_button").setAttribute("disabled", "true");
        clearInterval(intervalMessages);
        longRefresh();
    }
}

function successConnection(message) {
    if (message != "") {
        messages.push(message + "\n");
        document.getElementById("message").innerText = messages.join("");
        if (messages.length >= numMessages)
            messages.shift();
        if (message == "INFO - done") { 
            getResults();
            document.getElementById("run_button").removeAttribute("disabled");
            document.getElementById("stop_button").setAttribute("disabled", "true");
        }
        else { 
            shortRefresh();
        }
    } else {
        document.getElementById("run_button").removeAttribute("disabled");
        document.getElementById("stop_button").setAttribute("disabled", "true");
        longRefresh();
    }
}

function shortRefresh() {
    clearInterval(intervalMessages);
    intervalMessages = setInterval(getMessage, shortPeriod);
}

function longRefresh() {
    clearInterval(intervalMessages);
    intervalMessages = setInterval(getMessage, longPeriod);
}

function loadTopology() {
    var scenario = generateScenario();
    try {
        var message = null;
        $.ajax
            ({
                data: scenario,
                url: "load",
                type: "POST",
                async: false,
                success: function (ans) {
                    message = ans;
                }
            });
        if (message != null) {
            initializeGraph('cy1');
            cleanResults();
        }
        return message;
    }
    catch (e) {
        return 0;
    }
}

function runOpt() {
    loadTopology();
    shortRefresh();
    var scenario = generateScenario();
    try {
        var message = null;
        $.ajax
            ({
                data: scenario,
                url: "run",
                type: "POST",
                async: false,
                success: function (ans) {
                    message = ans;
                }
            });
        if (message == 201) {
            document.getElementById("run_button").disabled = true;
            document.getElementById("stop_button").disabled = false;
        }
    }
    catch (e) {
        return 0;
    }
}

function stopOpt() {
    longRefresh();
    try {
        var message = null;
        $.ajax
            ({
                url: "stop",
                type: "GET",
                async: false,
                success: function (ans) {
                    message = ans;
                }
            });
        if (message == 201) {
            document.getElementById("run_button").disabled = false;
            document.getElementById("stop_button").disabled = true;
        }
    }
    catch (e) {
        return 0;
    }
}

function setDecimals(value) {
    value = value.toFixed(1);
}

function generateScenario() {
    // model
    var inputFileName = document.getElementById("inputFileName").value;
    var objFunc = document.getElementById("objFunc").value;
    var maximization = $("#max").is(":checked");
    var scenarioName = document.getElementById("scenario").value;
    // general
    var RP1 = $("#RP1").is(":checked");
    var RP2 = $("#RP2").is(":checked");
    var PF1 = $("#PF1").is(":checked");
    var PF2 = $("#PF2").is(":checked");
    var PF3 = $("#PF3").is(":checked");
    var FD1 = $("#FD1").is(":checked");
    var FD2 = $("#FD2").is(":checked");
    var FD3 = $("#FD3").is(":checked");
    // model specific
    var sync_traffic = $("#sync-traffic").is(":checked");
    var max_serv_delay = $("#max-serv-delay").is(":checked");
    var cloud_only = $("#cloud-only").is(":checked");
    var edge_only = $("#edge-only").is(":checked");
    var single_path = $("#single-path").is(":checked");
    var set_init_plc = $("#set-init-plc").is(":checked");
    // other
    var const_vld = $("#const-vld").is(":checked");
    var force_src = $("#force-src").is(":checked");
    var force_dst = $("#force-dst").is(":checked");
    var const_rep = $("#const-rep").is(":checked");
    var scenario = JSON.stringify({
        inputFileName: inputFileName,
        objFunc: objFunc,
        maximization: maximization,
        name: scenarioName,
        constraints: {
            // general
            RP1: RP1,
            RP2: RP2,
            PF1: PF1,
            PF2: PF2,
            PF3: PF3,
            FD1: FD1,
            FD2: FD2,
            FD3: FD3,
            // additional
            sync_traffic: sync_traffic,
            max_serv_delay: max_serv_delay,
            cloud_only: cloud_only,
            edge_only: edge_only,
            single_path: single_path,
            set_init_plc: set_init_plc,
            // other
            const_vld: const_vld,
            force_src: force_src,
            force_dst: force_dst,
            const_rep: const_rep
        }
    });
    return scenario;
}