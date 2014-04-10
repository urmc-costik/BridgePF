angular.module('bridge').controller('MainController', ['$scope','$http', function($scope, $http) {
	
	function handle(data) {
		$scope.message = data.payload;
	}
	
	$scope.signIn = function() {
		var p = $http.post('/api/auth/signIn', {'username': 'test2', 'password': 'password'})
			.success(function(data) {
				$scope.message = "You are signed in.";
			}).error(function(data) {
				if (data.type === "TermsOfUseException") {
					$scope.message = "You must sign the terms of use. ";
				} else {
					$scope.message = data.payload;
				}
			});
	};
	$scope.signOut = function() {
		var p = $http.get('/api/auth/signOut')
			.success(handle).error(handle);
	};
	$scope.resetPassword = function() {
		var p = $http.post('/api/auth/resetPassword', {'email': 'test2@sagebase.org'})
			.success(handle).error(handle);
	};
	$scope.getUserProfile = function() {
		var p = $http.get('/api/auth/getUserProfile')
			.success(function(data) {
				$scope.message = data.emails.join(', ');
			}).error(handle);
	};
	$scope.bootstrap = function() {
		var p = $http.get('/bootstrap').success(handle).error(handle);
	};
}]);
