//var startTime = 1254326400000;// 2009-10-1
var startTime = 1451404800000;// 2015-12-30
var endTime = 0;

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

	// init pages
	var options = {
		currentPage : 1,
		totalPages : 1,
	}
	$('#pages').bootstrapPaginator(options);
}

function tsearch() {
	var keyword = $("#keyword")[0].value
	var topk = $("#topk")[0].value
	$.post("./ttks.do", {
		keyword : keyword,
		topk : topk,
		start : startTime,
		end : endTime,
		queryType : $("#queryType")[0].value
	}, handleQueryResults);
}
var ITEMS_PER_PAGE = 10
var mids = null

function handleQueryResults(jsonData) {
	mids = jsonData.mids
	// init pages
	var options = {
		currentPage : 1,
		totalPages : Math.ceil(jsonData.mids.length / ITEMS_PER_PAGE),
		onPageChanged : function(e, oldPage, newPage) {
			onPage(newPage);
		}
	}
	$('#pages').bootstrapPaginator(options);
	onPage(1);
}

function onPage(i) {
	var queryMids = mids.slice((i - 1) * ITEMS_PER_PAGE, i * ITEMS_PER_PAGE);
	var start = startTime
	var end = endTime
	$.post("./tweets.do", {
		mids : queryMids.join(","),
		startTime : start,
		endTime : end
	}, handleTimeSeriesData);
}
function options(timeSeries_xData, series, legend) {
	var option = {
		tooltip : {
			trigger : 'axis'
		},
		// legend : {
		// data : function() {
		// return legend;
		// }()
		// },
		toolbox : {
			x : 'left',
			show : true,
			feature : {
				magicType : {
					show : true,
					type : [ 'line', 'bar' ]
				},
				restore : {
					show : true
				},
				dataView : {
					show : true,
					readOnly : true
				},
				saveAsImage : {
					show : true
				}
			}
		},
		// calculable : true,
		dataZoom : {
			show : true,
			realtime : true,
			height : 50,
			start : 0,
			end : 100
		},
		grid : {
			y2 : 100
		},
		xAxis : [ {
			type : 'category',
			name : 'Time',
			boundaryGap : true,
			splitLine : {
				show : false
			},
			scale : true,
			data : function() {
				return timeSeries_xData;
			}()
		} ],
		yAxis : [ {
			type : 'value',
			name : '#Retweets',
			splitArea : {
				show : true
			}
		} ],
		series : function() {
			return series;
		}()
	}

	return option;
}

/**
 * Created by xiafan on 15/11/26.
 */

/**
 * Data: TimeSeriesData Dom: event-timeSeriesChart、eventReport1
 * 
 * @param jsonData
 */
function handleTimeSeriesData(jsonData) {
	timeSeriesData = jsonData;
	var timeSeries_xData = jsonData.timeAxis;
	var legend = []
	var series = []
	for ( var idx in jsonData.series) {
		var row = jsonData.series[idx];
		legend.push(idx)
		series.push({
			name : idx,
			type : 'line',
			data : row
		})
	}

	var colorMap = null;
	require([ 'echarts', 'echarts/chart/line' ], function(ec) {
		eventPanel1Chart = ec.init(document
				.getElementById('tweet-timeSeriesChart'));
		eventPanel1Chart.setOption(options(timeSeries_xData, series, legend));
		colorMap = eventPanel1Chart.chart.line._sIndex2ColorMap;
		// show the content
		var html = "";
		var tIdx = 0;
		for ( var idx in jsonData.tweets) {
			var status = eval('(' + jsonData.tweets[idx] + ')');
			html += "<div id = '" + idx + "'><h5><font color='"
					+ colorMap[tIdx] + "'>" + status.text
					+ "</font></h5><h5><small>" + status.uname
					+ "</small></h5><hr></div>";
			tIdx = tIdx + 1;
		}
		$("#tweets")[0].innerHTML = html;
	});
}

if (document.addEventListener) {
	document.addEventListener('DOMContentLoaded', setupPage, false);
} else {
	window.onload = setupPage;
}
