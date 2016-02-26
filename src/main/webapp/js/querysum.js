/**
 * Created by liye on 15/11/26. modified by xiafan
 */
//var startTime = 1451404800000;// 2015-12-30
var startTime = 1230739200000;// 2015-12-30
var endTime = 0;
var curEventIdx = 0;
var eventTimeline = {
		events : [ {
		mids : [ 1, 2 ],
		startTime : 10,
		endTime : 10,
		words:{"微博":0.4,"测试":0.1,"热门":0.8,}
	} ]}

function setupPage() {
			require.config({
				paths : {
					// echarts: 'http://echarts.baidu.com/build/dist' 百度在线地址
					echarts : './js/echart/'
				}
			});

			var curTime = new Date();
			endTime = curTime.getTime();
			var startTimeDate = new Date();
			startTimeDate.setTime(startTime);
			$("#starttime")[0].innerHTML = startTimeDate.toLocaleString();
			$("#endtime")[0].innerHTML = curTime.toLocaleString();
			$('#ex1').slider({
				min : startTime,
				max : endTime,
				value : [ startTime, endTime ],
				step : 3600 * 1000,
				formatter : function(value) {
					return "";
				}
			});
			$('#ex1').on('slide', function(evt) {
				startTime = evt.value[0];
				endTime = evt.value[1];
				var start = new Date();
				start.setTime(startTime);
				var end = new Date();
				end.setTime(endTime);
				$('#starttime')[0].innerHTML = start.toLocaleString();
				$('#endtime')[0].innerHTML = end.toLocaleString();
			})
		}


function querySum() {
	var keyword = $("#keyword")[0].value
	var topk = $("#topk")[0].value
	$.post("./esum.do", {
		keyword : keyword,
		startTime : startTime,
		endTime : endTime,
		simType:$("#simType")[0].value,
		shouldStandard:$("#shouldStandard")[0].value,
		clusterAlg:$("#clusterAlg")[0].value,
		sumNum:$("#sumNum")[0].value,
		simThreshold:$("#simThreshold")[0].value,
		topk:$("#topk")[0].value
	}, handSummary);
}
function handSummary(jsonData){
	eventTimeline = JSON.parse(jsonData.timeline);
	var html = "";
	for(idx in eventTimeline.events){
		var statuses = [];
		var mids = [];
		var statuses = eventTimeline.events[idx].statuses;
		html += '<li class="timeline-inverted">'+
		'<div class="timeline-badge">'+
			'<i class="glyphicon glyphicon-check"></i>'+
		'</div>'+
		'<div class="timeline-panel">'+
			'<div class="timeline-heading" onclick="displaySum('+idx+')">'+
				'<h4 class="timeline-title">'+"Title"+'</h4><p>'+
					'<small class="text-muted"><i'+
						'class="glyphicon glyphicon-time"></i>' +new Date(eventTimeline.events[idx].startTime).toLocaleString()+'</small></p></div>'+
			'<div class="timeline-body">'+statuses[0].text+'</div></div></li>';
		for(sIdx in statuses){
			mids.push(statuses[sIdx].mid);
		}
		eventTimeline.events[idx].mids = mids;
	}
	$(".timeline")[0].innerHTML=html;
	displaySum(curEventIdx);
}

function displaySum(idx) {
	handleDataOfCloud(eventTimeline.events[idx]);
	var statuses = eventTimeline.events[idx].statuses;
	displayHotTweets(statuses);
	var omids=[];
	var omidsMap={};
	for(sIdx in statuses){
		if (statuses[sIdx].omid <0){
			omidsMap[statuses[sIdx].mid]=1;
		}else{
			omidsMap[statuses[sIdx].omid]=1;
		}
	}
	for(omid in omidsMap){
		omids.push(omid);
	}
	$.post("rtstats/tseries.do", {
		mids : omids.join(","),
		startTime : eventTimeline.events[idx].startTime,
		endTime : eventTimeline.events[idx].endTime,
	}, handleEventTimeSeriesData);

	// user related
	$.post("rtstats/user.do", {
		mids : omids.join(","),
		startTime : eventTimeline.events[idx].startTime,
		endTime : eventTimeline.events[idx].endTime,
	}, handleUserData);
	
	// loc
	$.post("rtstats/loc.do", {
		mids : omids.join(","),
		startTime : eventTimeline.events[idx].startTime,
		endTime : eventTimeline.events[idx].endTime,
	}, handleLocationData);
	// mood
	$.post("rtstats/mood.do", {
		mids : omids.join(","),
		startTime : eventTimeline.events[idx].startTime,
		endTime : eventTimeline.events[idx].endTime,
	}, handleDataOfMood);
	//

}

/**
 * Data: TimeSeriesData Dom: event-timeSeriesChart、eventReport1
 * 
 * @param jsonData
 */
function handleEventTimeSeriesData(jsonData) {
	timeSeries_xData = jsonData.timeAxis;
	
	for ( var idx in jsonData.series) {
		timeSeries_sLine1=jsonData.series[idx]
	}

	require([ 'echarts', 'echarts/chart/line', 'echarts/chart/bar',
			'echarts/chart/pie' ], function(ec) {
		eventPanel1Chart = ec.init(document
				.getElementById('event-timeSeriesChart'));
		eventPanel1Chart.setOption(options_event(timeSeries_xData,
				timeSeries_sLine1));
	});
}

/**
 * Data: eventUser Dom: event-userChart1,event-userChart2,event-userChart3
 * 
 * @param jsonData
 */
function handleUserData(jsonData) {

	require([ 'echarts', 'echarts/chart/pie', 'echarts/chart/funnel' ],
			function(ec) {
				eventPanel2Chart1 = ec.init(document
						.getElementById('event-userChart1'));
				eventPanel2Chart1.setOption(options_2(
						jsonData.genderDist.m,
						jsonData.genderDist.f));

				eventPanel2Chart2 = ec.init(document
						.getElementById('event-userChart2'));
				eventPanel2Chart2.setOption(options_3(jsonData.vipDist.true,
						jsonData.vipDist.false));

				var clients=[]
				for(client in jsonData.clientDist){
					clients.push({name:client, value: jsonData.clientDist[client]})
				}
				
				eventPanel2Chart3 = ec.init(document
						.getElementById('event-userChart3'));
				eventPanel2Chart3.setOption(options_plat(clients));
			});

	var u = (jsonData.vipDist[true]
			/ (jsonData.vipDist[true] + jsonData.vipDist[false]) * 100)
			.toFixed(2);
	var reportHtml = "";
	reportHtml += "<div><h4><span class='glyphicon glyphicon-hand-right'></span> faction of VIP users: </h4>";
	reportHtml += "<h4 style='margin-left:25px'><small>" + u
			+ "%</small></h4></div>";

	$("#eventReport2").html(reportHtml);
}

/**
 * Data: eventLocation Dom: event-locationChart,eventReport4
 * 
 * @param jsonData
 */
function handleLocationData(jsonData) {
	var location_mapData = [];
	var location_maxCount = 0;
	for ( var idx in jsonData.locDist) {
		var val = jsonData.locDist[idx];
		location_mapData.push({
			name : idx,
			value : val
		});
		location_maxCount = location_maxCount + 1;
	}

	require([ 'echarts', 'echarts/chart/map' ], function(ec) {
		eventPanel3Chart = ec.init(document
				.getElementById('event-locationChart'));
		eventPanel3Chart.setOption(options_5(location_mapData,
				location_maxCount));
	});
}

/**
 * Data: eventMood Dom: event-moodChart,eventReport6
 * 
 * @param jsonData
 */
function handleDataOfMood(jsonData) {
	var moodData = [];
	var max = 0;
	var maxMood = "";
	var tempIndicator = [];
	var indicator = [];

	var temp = [];
	var total = 0;
	for ( var mood in jsonData.moodDist) {
		var count = jsonData.moodDist[mood];
		// if (idx == "快乐")
		// row.count = parseInt(row.count / 4);
		if (count > max) {
			max = count;
			maxMood = mood;
		}
		tempIndicator.push(mood);
		temp.push(count);
		total += count;
	}
	var add = ((max - parseInt(total / 6)) / max * 100).toFixed(2);
	var tempP = [];
	for (var i = 0; i <= 6; i++) {
		tempP.push(parseInt(temp[i] / total * 100));
	}

	for (var i = 0; i < tempIndicator.length; i++)
		indicator.push({
			text : tempIndicator[i],
			max : parseInt(max / total * 100)
		});

	moodData.push({
		value : tempP,
		name : '民众情绪分布'
	});
	require([ 'echarts', 'echarts/chart/radar', ],
			function(ec) {
				eventPanel4Chart2 = ec.init(document
						.getElementById('event-moodChart'));
				eventPanel4Chart2.setOption(options_6(indicator, moodData));
			});

	// report
	var reportHtml = "";
	reportHtml += "<div><h4><span class='glyphicon glyphicon-hand-right'></span> 民众情绪主导: </h4>";
	reportHtml += "<h4 style='margin-left:25px'><small>事件中民众情绪主要持" + maxMood
			+ "态度,占比达到" + add + "%</small></h4></div>";

	$("#eventReport6").html(reportHtml);
}

function displayHotTweets(statuses){
	var html = "";
	var tIdx = 0;
	for ( var idx in statuses) {
		var status = statuses[idx];
		html += "<div id = '" + idx + "'><h5>" + status.text
				+ "</font></h5><h5><small>" + status.uname
				+ "</small></h5><hr></div>";
		tIdx = tIdx + 1;
	}
	$("#tweets")[0].innerHTML = html;
}

function handleDataOfCloud(jsonData) {
	var cloudData = [];
	var min = 1000000;
	var max = 0;

	for ( var word in jsonData.words) {
		var weight = jsonData.words[word];
		cloudData.push([ word, weight ]);
		if (weight > max)
			max =weight;
		if (weight < min)
			min = weight;
	}
	drawCloud(cloudData, min, max);
}

function drawCloud(cloudData, min, max) {
	var fill = d3.scale.category20();
	var fontSize = d3.scale.log().range([ 50, 200 ]);
	var angle = [ 0, 90 ];
	var range = max - min;
	if (range == 0)
		range = min;

	d3.layout.cloud().size([ 500, 350 ]).words(cloudData.map(function(d) {
		return {
			text : d[0],
			size : cloudScale(d[1], min, range)
		};
	})).padding(5).rotate(function() {
		var idx = Math.floor(Math.random() + 0.3);
		return ~~(angle[idx]);
	}).font("Impact").fontSize(function(d) {
		return d.size;
	}).on("end", draw).start();

	function draw(words) {
		$("#event-cloudChart")[0].innerHTML="";
		$("#event-cloudChart")[0].innerText="";
		d3.select("#event-cloudChart").append("svg").attr("width", 500).attr(
				"height", 350).append("g").attr("transform",
				"translate(250,175)").selectAll("text").data(words).enter()
				.append("text").style("font-size", function(d) {
					return d.size + "px";
				}).style("font-family", "Impact").style("fill", function(d, i) {
					return fill(i);
				}).attr("text-anchor", "middle").attr(
						"transform",
						function(d) {
							return "translate(" + [ d.x, d.y ] + ")rotate("
									+ d.rotate + ")";
						}).text(function(d) {
					return d.text;
				}).on("click", function(d) {
					$("#keyword:text").val(function(n, c) {
						return d.text;
					});
				});
	}
}

function cloudScale(freq, min, range) {
	return (50 / range) * (freq - min) + 15;
}

if (document.addEventListener) {
	document.addEventListener('DOMContentLoaded', setupPage, false);
} else {
	window.onload = setupPage;
}