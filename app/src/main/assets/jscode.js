window.androidObj = function AndroidClass(){};

function showAndroidToast(toast) {
    var value = Android.showToast(toast);
    if (value) {
        document.getElementById("h1").innerHTML = "I got callback data from android!";
    }
}

function callbackFunc(data) {
    document.getElementById("p1").innerHTML = data;
}

function requestLocationPermission() {
    var value = Android.getLocationPermission();
    if (value) {
        document.getElementById("h1").innerHTML = "Location permission granted!";
    } else {
        document.getElementById("h1").innerHTML = "Location permission declined!";
    }
}

function changeTitle(title) {
    document.getElementById("h1").innerHTML = title;
}

function changeSubtitle(subtitle) {
    document.getElementById("p1").innerHTML = subtitle;
}

class Thenable {
  constructor(num) {
    this.num = num;
  }
  then(resolve, reject) {
    alert(resolve);
    // resolve with this.num*2 after 1000ms
    setTimeout(() => resolve(this.num * 2), 1000); // (*)
  }
};

function callAsyncFunc() {
    Android.changeTitleAsync("changeTitle");
    let result = new Thenable(1);
    changeSubtitle("Subtitle changed at line:39");
}

function storeJwtToken(token) {
    console.log("storing JWT!!!");
    localStorage.setItem("jwtToken", token);
}

function showJwt() {
    document.getElementById("h1").innerHTML = localStorage.getItem("jwtToken");
}

function showPic(name, path) {
    console.log("came to show pic!");
    console.log('data:image/jpeg;base64,'+path)
    document.getElementById("h3").innerHTML = name
    document.getElementById("img").src = 'data:image/jpeg;base64,'+path;
}

function showContact(name, contact) {
    document.getElementById("h1").innerHTML = name;
    changeSubtitle(contact);
}

var options = {
  enableHighAccuracy: true,
  timeout: 5000,
  maximumAge: 0
};

function success(pos) {
  var crd = pos.coords;

  console.log('Your current position is:');
  console.log(`Latitude : ${crd.latitude}`);
  console.log(`Longitude: ${crd.longitude}`);
  console.log(`More or less ${crd.accuracy} meters.`);

  changeSubtitle(`Latitude : ${crd.latitude} Longitude: ${crd.longitude}`);
}

function error(err) {
  console.warn(`ERROR(${err.code}): ${err.message}`);
}

function geoLocation() {
    console.log("in geolocation");
    navigator.geolocation.getCurrentPosition(success, error, options);
}

function clickPic() {
    OnboardingInterface.execute("{\n    \"request_type\": \"PHOTO\",\n    \"parameters\": {\"web_identifier\": \"IDENTIFIER_PHOTO\"}\n}");
}

function getContact() {
    OnboardingInterface.execute("{\n    \"request_type\": \"CONTACT\",\n    \"parameters\": {\"web_identifier\": \"IDENTIFIER_CONTACT\"}\n}");
}

function getJwt() {
    OnboardingInterface.execute("{\n    \"request_type\": \"GET_JWT\",\n    \"parameters\": {\"web_identifier\": \"IDENTIFIER_GET_JWT\"}\n}");
}

function refreshJwt() {
    OnboardingInterface.execute("{\n    \"request_type\": \"REFRESH_JWT\",\n    \"parameters\": {\"web_identifier\": \"IDENTIFIER_REFRESH_JWT\"}\n}");
}

function sendEvent() {
    OnboardingInterface.execute("{\n    \"request_type\": \"SEND_EVENT\",\n    \"parameters\": {\n        \"event_name\": \"dummy_event_name\",\n        \"event_meta\": {\n            \"dummy_event_property\": \"dummy_value\"\n        }\n    }\n}");
}

function nativeInterfaceCallback(response) {
    console.log("Response from Interface : " + response);
    var obj = JSON.parse(response);
    console.log("TYPE : " + obj.response_type);
    console.log("WEB_IDENTIFIER : " + obj.web_identifier);
    if (obj.response_type == "PHOTO") {
        showPic(obj.data.name, obj.data.base_64_string);
    } else if (obj.response_type == "CONTACT") {
        showContact(obj.data.contact_name, obj.data.contact_list[0]);
    } else if (obj.response_type == "GET_JWT") {
        changeSubtitle(obj.data.jwt_token)
    } else if (obj.response_type == "REFRESH_JWT") {
        changeSubtitle(obj.data.jwt_token)
    }
}