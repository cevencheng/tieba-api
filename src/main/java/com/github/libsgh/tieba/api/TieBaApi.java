package com.github.libsgh.tieba.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.github.libsgh.tieba.model.ClientType;
import com.github.libsgh.tieba.model.MyTB;
import com.github.libsgh.tieba.model.ReplyInfo;
import com.github.libsgh.tieba.model.WatermarkType;
import com.github.libsgh.tieba.util.Constants;
import com.github.libsgh.tieba.util.HttpKit;
import com.github.libsgh.tieba.util.JsonKit;
import com.github.libsgh.tieba.util.StrKit;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;

/**
 * 贴吧api
 * @author libs
 * 2018-4-8
 */
public class TieBaApi {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public  static volatile TieBaApi api;
	
	private HttpKit hk = HttpKit.getInstance();
	
	public static TieBaApi getInstance() {
        if (api == null) {
            synchronized (TieBaApi.class) {
                if (api == null) {
                	api = new TieBaApi();
                }
            }
        }
        return api;
    }
	
	/**
	 * 百度登录（获取bduss、stoken）
	 * @param userName 用户名
	 * @param password 密码
	 * @param verifyCode 输入的验证码
	 * @param codeString 验证码code（服务器返回的）
	 * @param cookie cookie
	 * @param token token
	 * @return map 登录信息
	 */
	public Map<String, Object> getBaiDuLoginCookie(String userName, String password, String verifyCode,
			String codeString, String cookie, String token){
		String bduss = "";
    	String stoken = "";
    	String ptoken = "";
    	String codestring = "";
    	Header[] headerArr = null;
		Map<String, Object> map = new HashMap<String, Object>();
		try {
			if(StrKit.isBlank(token)){
				//1.访问百度首页，获取cookie baiduid
				hk.execute(Constants.BAIDU_URL);
				//2.保持会话获取token
				token = this.token();
			}
			//3.password 加密
			String keyInfo = EntityUtils.toString(hk.execute(String.format(Constants.PUB_KEY_URL, api.token(), System.currentTimeMillis()+"")).getEntity());
			String rsakey = (String) JsonKit.getInfo("key", keyInfo);
			String pubkey = (String) JsonKit.getInfo("pubkey", keyInfo);
			pubkey = StrKit.substring(pubkey, "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----").replaceAll("\n", "");
			byte[] encrypt = SecureUtil.rsa(null, pubkey).encrypt(StrUtil.bytes(password, CharsetUtil.CHARSET_UTF_8), KeyType.PublicKey);
			password = Base64.encode(encrypt);
			//4.提交百度登录
			HashMap<String, Header> headerMaps = new HashMap<String, Header>();
			headerMaps.put("Referer",  new BasicHeader("Referer", "https://www.baidu.com/"));
			headerMaps.put("Host", new BasicHeader("Host", "passport.baidu.com"));
			HttpResponse response = hk.execute(Constants.LOGIN_POST_URL, cookie,
					genercFormEntity(userName, password, token,
					verifyCode, codeString, rsakey), headerMaps);
			String result = EntityUtils.toString(response.getEntity());
			String statusCode = StrKit.substring(result, "err_no=", "&");
			switch(statusCode) {  
			    case "0":
			    	//登录成功
			    	map.put("status", 0);
					map.put("message", "登录成功");
					//获取百度cookie（bduss、stoken）
			    	headerArr = response.getHeaders("Set-Cookie");
					for (Header header : headerArr) {
						String cookieHeader = header.getValue();
						if(cookieHeader.contains("BDUSS=")){
							bduss = StrKit.substring(cookieHeader, "BDUSS=", ";");
							map.put("bduss", bduss);
						}else if(cookieHeader.contains("PTOKEN=")){
							ptoken = StrKit.substring(cookieHeader, "PTOKEN=", ";");
							map.put("ptoken", ptoken);
						}
					}
					stoken = hk.doGetStoken(Constants.PASSPORT_AUTH_URL,createCookie(bduss, null, ptoken));
					map.put("stoken", stoken);
			    break;  
			    case "18":
			    	//探测到您的帐号存在安全风险，建议关联手机号提高安全性(未绑定手机)
			    	map.put("status", 0);
			    	map.put("message", "登录成功");
			    	//获取百度cookie（bduss、stoken）
			    	headerArr = response.getHeaders("Set-Cookie");
					for (Header header : headerArr) {
						String cookieHeader = header.getValue();
						if(cookieHeader.contains("BDUSS=")){
							bduss = StrKit.substring(cookieHeader, "BDUSS=", ";");
							map.put("bduss", bduss);
						}else if(cookieHeader.contains("PTOKEN=")){
							ptoken = StrKit.substring(cookieHeader, "PTOKEN=", ";");
							map.put("ptoken", ptoken);
						}
					}
					stoken = hk.doGetStoken(Constants.PASSPORT_AUTH_URL,createCookie(bduss, null, ptoken));
					logger.debug("bduss:\t"+bduss);
					logger.debug("ptoken:\t"+ptoken);
					logger.debug("stoken:\t"+stoken);
					map.put("stoken", stoken);
			    	break;  
			    case "400031":
			    	//账号开启了登录保护
			    	map.put("status", -2);
					map.put("message", "账号开启了登录保护，请关闭");
			    	break;  
			    case "4":
			    	//用户名或密码错误
			    	map.put("status", -3);
					map.put("message", "用户名或密码错误");
					break;  
			    case "257":
			    	//请输入验证码
			    	codestring = StrKit.substring(result, "&codeString=", "&userName");
			    	map.put("status", -1);
					map.put("message", "请输入验证码");
					map.put("imgUrl", Constants.CAPTCHA_IMG+"?"+codestring);
					map.put("cookies", hk.getCookies());
					map.put("codestring", codestring);
					map.put("token", token);
					break;
			    case "6":
			    	//验证码错误
			    	codestring = StrKit.substring(result, "&codeString=", "&userName");
			    	map.put("status", -1);
			    	map.put("message", "请输入验证码");
			    	map.put("imgUrl", Constants.CAPTCHA_IMG+"?"+codestring);
			    	map.put("cookies", hk.getCookies());
			    	map.put("codestring", codestring);
			    	map.put("token", token);
			    	break;
			    default:
			    	//其他未知错误
			    	map.put("status", -4);
					map.put("message", "其他错误");
			    break;  
			}  
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return map;
	}
	
	/**
	 *  登录POST参数
	 * @param userName 用户名
	 * @param password 密码 rsa加密 base64
	 * @param token token
	 * @param verifyCode 输入的验证码
	 * @param codeString 验证码图片code
	 * @param rsakey rsakey
	 * @return 参数列表
	 * @throws Exception
	 */
	private List<NameValuePair> genercFormEntity(String userName, String password, String token,
			String verifyCode, String codeString, String rsakey) throws Exception{
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("staticpage", "http://tieba.baidu.com/tb/static-common/html/pass/v3Jump.html"));
		list.add(new BasicNameValuePair("charset", "UTF-8"));
		list.add(new BasicNameValuePair("token", api.token()));
		list.add(new BasicNameValuePair("tpl", "tb"));
		list.add(new BasicNameValuePair("apiver", "v3"));
		list.add(new BasicNameValuePair("tt", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("codestring", codeString));
		list.add(new BasicNameValuePair("safeflg", "0"));
		list.add(new BasicNameValuePair("u", "https://tieba.baidu.com/index.html"));
		list.add(new BasicNameValuePair("isPhone", ""));
		list.add(new BasicNameValuePair("detect", "1"));
		list.add(new BasicNameValuePair("gid", StrKit.createGid()));
		list.add(new BasicNameValuePair("quick_user", "0"));
		list.add(new BasicNameValuePair("logintype", "logintype"));
		list.add(new BasicNameValuePair("logLoginType", "pc_loginDialog"));
		list.add(new BasicNameValuePair("idc", ""));
		list.add(new BasicNameValuePair("loginmerge", "true"));
		list.add(new BasicNameValuePair("splogin", "rate"));
		list.add(new BasicNameValuePair("username", userName));
		list.add(new BasicNameValuePair("password", password));
		list.add(new BasicNameValuePair("mem_pass", "on"));
		list.add(new BasicNameValuePair("rsakey", rsakey));
		list.add(new BasicNameValuePair("crypttype", "12"));
		list.add(new BasicNameValuePair("ppui_logintime", "27647"));
		list.add(new BasicNameValuePair("loginversion", "v4"));
		list.add(new BasicNameValuePair("verifycode", verifyCode));
		return list;
	}
	
	/**
	 * 获取登陆token
	 * @return token
	 * @throws Exception 异常
	 */
	public String token() throws Exception{
		String token = null;
		HttpResponse response = hk.execute(Constants.TOKEN_GET_URL);
		String str = EntityUtils.toString(response.getEntity());
		Pattern pattern = Pattern.compile("token\" : \"(.*?)\"");
		Matcher matcher = pattern.matcher(str);
		if(matcher.find()){
			token = matcher.group(1);
		}
		return token;
	}
	
	/**
	 * 一键签到所有贴吧
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return 签到结果
	 */
	public Map<String, Object> oneBtnToSign(String bduss, String stoken){
		Long start = System.currentTimeMillis();
		Map<String, Object> msg = new HashMap<String, Object>();
		//1.先获取用户关注的贴吧
		List<MyTB> list = getMyLikedTB(bduss, stoken);
		String tbs = this.getTbs(bduss);
		int totalCount = list.size();
		//2.一键签到
		List<Map<String, Object>> results = list.stream()
			.parallel()
			.map(tb -> {
				return this.signOneTieBa(tb.getTbName(), tb.getFid(), bduss, tbs);
		}).collect(Collectors.toList());
		long signCount = results.stream().filter(r -> r.get("error_code").toString().equals("0")).count();
		long signedCount = results.stream().filter(r -> r.get("error_code").toString().equals("160002")).count();
		msg.put("用户贴吧数", totalCount);
		msg.put("签到成功", signCount);
		msg.put("已签到", signedCount);
		msg.put("签到失败", (totalCount - signedCount - signCount));
		msg.put("耗时", (System.currentTimeMillis()-start)+"ms");
		return msg;
	}
	
	/**
	 * 执行签到
	 * @param tbName 想要签到的贴吧
	 * @param fid 贴吧fid
	 * @param bduss bduss
	 * @param tbs tbs
	 * @return 签到结果
	 */
	@SuppressWarnings({ "resource", "unchecked" })
	public Map<String, Object> signOneTieBa(String tbName, int fid, String bduss, String tbs){
		Map<String, Object> tb = new HashMap<String, Object>();
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "03-00-DA-59-05-00-72-96-06-00-01-00-04-00-4C-43-01-00-34-F4-02-00-BC-25-09-00-4E-36"));
			list.add(new BasicNameValuePair("_client_type", "4"));
			list.add(new BasicNameValuePair("_client_version", "1.2.1.17"));
			list.add(new BasicNameValuePair("_phone_imei", "540b43b59d21b7a4824e1fd31b08e9a6"));
			list.add(new BasicNameValuePair("fid",  new Formatter().format("%d", fid).toString()));
			list.add(new BasicNameValuePair("kw", tbName));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("tbs", tbs));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			
			HttpResponse response = hk.execute(Constants.SIGN_POST_URL, createCookie(bduss), list);
	        String result = EntityUtils.toString(response.getEntity());
	        if(logger.isDebugEnabled()) {
	        	logger.debug(result);
	        }
	        String code = (String) JsonKit.getInfo("error_code", result);
	        String msg = (String) JsonKit.getInfo("error_msg", result);
	        Map<String, String> map = (Map<String, String>) JsonKit.getInfo("user_info", result);
	        if("0".equals(code)){//签到成功
	        	String signPoint = map == null ? "0" : map.get("sign_bonus_point");
	            if(signPoint.equals("0")){
	            	//百度抽风，签到失败，重签
	            	this.signOneTieBa(tbName, fid, bduss);
	            }
	            tb.put("exp", signPoint);
	            tb.put("countSignNum", map==null?0:Integer.parseInt(map.get("cont_sign_num")));
	            tb.put("signTime", realTime("Asia/Shanghai"));
	            tb.put("error_msg", "签到成功");
	            //tb.set("signTime", Integer.parseInt(map.get("sign_time"))*1000);
	        }else if("160002".equals(code)){
	        	logger.debug("亲，你之前已经签过了");
	        }else if("340006".equals(code)){
	            logger.debug("贴吧本身原因导致的签到失败，如贴吧被封");
	        }else if("1990055".equals(code)){
	        	logger.debug("帐号未实名，功能禁用。请先完成帐号的手机实名验证");
	        }
	        if(StrKit.notBlank(msg)){
	        	 tb.put("error_msg", new String(msg));
	        }
	        tb.put("error_code", code);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
        return tb;
	}
	
	/**
	 * 执行签到（尽量不要每个贴吧签到都获取tbs，因为频繁的获取tbs可能会导致404，每个用户在签到前获取一次就行）
	 * @param tbName 贴吧名称
	 * @param fid 贴吧fid
	 * @param bduss bduss
	 * @return 签到结果
	 */
	public Map<String, Object> signOneTieBa(String tbName, int fid, String bduss){
		return this.signOneTieBa(tbName, fid, bduss, getTbs(bduss));
	}
	
	/**
	 * 获取tbs
	 * @param bduss bduss
	 * @return tbs
	 */
	public String getTbs(String bduss){
		try {
			HttpResponse response = hk.execute(Constants.TBS_URL, this.createCookie(bduss));
			String result = EntityUtils.toString(response.getEntity());
			return (String) JsonKit.getInfo("tbs", result);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 获取用户隐藏贴吧
	 * @param username 用户名
	 * @param curpn 页码
	 * @return result 贴吧列表
	 */
	public List<Map<String, Object>> getHideTbs(String username, Integer curpn) {
		List<Map<String, Object>> tiebas = new ArrayList<Map<String, Object>>();
		String uid = this.getUidByUname(username);
		if(StrUtil.isNotBlank(uid)) {
			this.getTbsByUid(uid, tiebas, 1, curpn, "");
		}else {
			logger.info("用户信息查找失败");
		}
		return tiebas;
	}
	
	/**
	 * 用户个人信息查询
	 * @param name 用户名
	 * @return 返回结果
	 */
	public String getUserProfile(String name) {
		String result = "";
		try {
			//1.根据名称获取uid
			String uid = this.getUidByUname(name);
			if(StrUtil.isNotBlank(uid)) {
				//2.根据uid获取用户信息
				List<NameValuePair> list = new ArrayList<NameValuePair>();
				list.add(new BasicNameValuePair("_client_version", "6.1.2"));//显示徽章必填项
				list.add(new BasicNameValuePair("has_plist", "2"));//1可以显示回帖信息
				list.add(new BasicNameValuePair("need_post_count", "1"));//查看回帖发帖数量必填
				list.add(new BasicNameValuePair("uid", uid));//用户的uid  必填
				list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
				HttpResponse response = hk.execute(Constants.USER_PROFILE, null, list);
				result = EntityUtils.toString(response.getEntity());
				if(logger.isDebugEnabled()) {
					logger.debug(result);
				}
			}else {
				logger.info("用户信息查找失败");
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	    return result;
	}
	/**
	 * 获取用户的礼物数量
	 * @param name name
	 * @return 返回结果
	 */
	public String getUserGiftNum(String name){
		try {
			//1.根据名称获取uid
			HttpResponse response0 = hk.execute(Constants.NEW_HEAD_URL + name,null);
			String tieba_user = EntityUtils.toString(response0.getEntity());
			if(tieba_user.contains("gift-num")){
				//有礼物
				return StrKit.substring(tieba_user, "(<i>", "</i>)");
			}else{
				return "0";
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "0";
	}
	
	/**
	 * 关注一个人
	 * @param portrait portrait
	 * @param bduss bduss
	 * @return 返回结果
	 */
	public String follow(String portrait, String bduss){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS",bduss));
			list.add(new BasicNameValuePair("portrait", portrait));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			list.add(new BasicNameValuePair("timestamp", String.valueOf(System.currentTimeMillis())));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.FOLLOW, null, list);
			String result = EntityUtils.toString(response.getEntity());
			return result;
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	
	/**
	 * 取消关注一个人
	 * @param portrait portrait
	 * @param bduss bduss
	 * @return 返回结果
	 */
	public String unfollow(String portrait, String bduss){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS",bduss));
			list.add(new BasicNameValuePair("portrait", portrait));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			list.add(new BasicNameValuePair("timestamp", String.valueOf(System.currentTimeMillis())));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.UNFOLLOW, null, list);
			String result = EntityUtils.toString(response.getEntity());
			return result;
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 关注一个贴吧
	 * @param BDUSS BDUSS
	 * @param kw 贴吧名称
	 * @param fid fid
	 * @return 返回结果
	 */
	public Boolean focus(String BDUSS, String kw, String fid) {
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", BDUSS));
			list.add(new BasicNameValuePair("fid", fid));
			list.add(new BasicNameValuePair("kw", kw));
			list.add(new BasicNameValuePair("tbs", getTbs(BDUSS)));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.LIKE_TIEBA_URL, null, list);
			String retCode = (String) JsonKit.getInfo("error_code", EntityUtils.toString(response.getEntity()));
			if(retCode.equals("0")) {
				return Boolean.TRUE;
			}
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return Boolean.FALSE;
	}
	
	/**
	 * 关注一个贴吧(网页接口)
	 * @param BDUSS BDUSS
	 * @param STOKEN STOKEN
	 * @param fid fid
	 * @return 返回结果
	 */
	public Boolean focus2(String BDUSS, String STOKEN, String fid) {
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("fid", fid));
			list.add(new BasicNameValuePair("tbs", getTbs(BDUSS)));
			HttpResponse response = hk.execute(Constants.ADD_TIEBA, createCookie(BDUSS, STOKEN), list);
			String result = EntityUtils.toString(response.getEntity());
			if (logger.isDebugEnabled()) {
				logger.debug("fid:{},result:{}", fid, result);
			}
			Integer retCode = (Integer) JsonKit.getInfo("no", result);
			if(retCode == 0 || retCode == 221) {
				//已关注或是关注成功
				return Boolean.TRUE;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return Boolean.FALSE;
	}
	
	/**
	 * 取关一个贴吧(网页接口)
	 * @param BDUSS BDUSS
	 * @param STOKEN STOKEN
	 * @param fid fid
	 * @return 返回结果
	 */
	public Boolean unfocus2(String BDUSS, String STOKEN, String fid) {
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("fid", fid));
			list.add(new BasicNameValuePair("tbs", getTbs(BDUSS)));
			HttpResponse response = hk.execute(Constants.DELETE_TIEBA, createCookie(BDUSS, STOKEN), list);
			String result = EntityUtils.toString(response.getEntity());
			if (logger.isDebugEnabled()) {
				logger.debug("fid:{},result:{}", fid, result);
			}
			Integer retCode = (Integer) JsonKit.getInfo("no", result);
			if(retCode == 0) {
				return Boolean.TRUE;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return Boolean.FALSE;
	}
	
	/**
	 * 关注一个贴吧
	 * @param BDUSS BDUSS
	 * @param kw 贴吧名称
	 * @return 返回结果
	 */
	public Boolean focus(String BDUSS, String kw) {
		return focus(BDUSS, kw, getFid(kw));
	}
	
	/**
	 * 取消关注一个贴吧
	 * @param BDUSS BDUSS
	 * @param kw 贴吧名称
	 * @param fid fid
	 * @return 返回结果
	 */
	public Boolean unfocus(String BDUSS, String kw, String fid) {
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", BDUSS));
			list.add(new BasicNameValuePair("fid", fid));
			list.add(new BasicNameValuePair("kw", kw));
			list.add(new BasicNameValuePair("tbs", getTbs(BDUSS)));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.UNFAVO_TIEBA_URL_NEW, null, list);
			String result = EntityUtils.toString(response.getEntity());
			String retCode = (String) JsonKit.getInfo("error_code", result);
			if(retCode.equals("0")) {
				return Boolean.TRUE;
			}
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return Boolean.FALSE;
	}
	
	/**
	 * 取消关注一个贴吧
	 * @param BDUSS BDUSS
	 * @param kw 贴吧名称
	 * @return 返回结果
	 */
	public Boolean unfocus(String BDUSS, String kw) {
		return unfocus(BDUSS, kw, getFid(kw));
	}
	
	/**
	 * 获取用户隐藏贴吧（接口失效）
	 * @param username 用户名
	 * @return result
	 */
	@Deprecated
	public List<Map<String, Object>> getHideTbs(String username) {
		return this.getHideTbs(username, null);
	}
	
	/**
	 * 获取用户隐藏贴吧（接口失效）
	 * @param uid 用户uid
	 * @return result
	 */
	public List<Map<String, Object>> getHideTbsByUid(String uid) {
		List<Map<String, Object>> tiebas = new ArrayList<Map<String, Object>>();
		try {
			this.getTbsByUid(uid, tiebas, 1, null, "");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return tiebas;
	}
	
	/**
	 * 递归获取关注贴吧数
	 * @param uid uid
	 * @param tiebas 贴吧列表
	 * @param page page
	 * @param curpn curpn
	 */
	@SuppressWarnings("unchecked")
	public void getTbsByUid(String uid, List<Map<String, Object>> tiebas, int page, Integer curpn, String bduss) {
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("_client_version", "6.2.2"));
		//list.add(new BasicNameValuePair("friend_uid", uid));
		list.add(new BasicNameValuePair("is_guest", "0"));
		list.add(new BasicNameValuePair("page_no", (curpn == null?page:curpn) + ""));
		list.add(new BasicNameValuePair("uid", uid));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		String tStr = "";
		try {
			tStr = EntityUtils.toString(hk.execute(Constants.GET_USER_TIEBA, createCookie(bduss), list).getEntity());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		String  hasMore =  JsonKit.getInfo("has_more", tStr).toString();
		Map<String, Object> j1 = new HashMap<String, Object>();
		if (!(JsonKit.getInfo("forum_list", tStr) instanceof com.alibaba.fastjson.JSONArray)) {
			j1 = (Map<String, Object>) JsonKit.getInfo("forum_list", tStr);
			List<Map<String, Object>> lj3 = (List<Map<String, Object>>) j1.get("non-gconforum");
			if(lj3 != null) {
				lj3 = lj3.stream().map(r->{
					r.put("type", "non-gconforum");
					return r;
				}).collect(Collectors.toList());
			}
			if(lj3 != null) {
				tiebas.addAll(lj3);
			}
			List<Map<String, Object>> lj4 = (List<Map<String, Object>>) j1.get("gconforum");
			if(lj4!=null) {
				lj4 = lj4.stream().map(r->{
					r.put("type", "gconforum");
					return r;
				}).collect(Collectors.toList());
			}
			if(lj4 != null) {
				tiebas.addAll(lj4);
			}
			if(curpn == null) {
				if(hasMore.equals("1")) {
					page++;
					this.getTbsByUid(uid, tiebas, page, curpn, bduss);
				}
			}
		}
	}
	
	
	/**
	 * 获取我喜欢的贴吧（不带分页参数）
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return result
	 */
	public List<MyTB> getMyLikedTB(String bduss, String stoken){
		List<MyTB> list = new ArrayList<MyTB>();
		this.getMyLikedTB(bduss, stoken, list, "1");
		return list;
	}
	
	/**
	 * 获取用户信息（user_portrait）
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return result
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getUserInfo(String bduss, String stoken){
		try {
			HttpResponse response = hk.execute(Constants.USER_INFO_GET_URL, createCookie(bduss, stoken));
			String result = EntityUtils.toString(response.getEntity());
			if(logger.isDebugEnabled()) {
	        	logger.debug(result);
	        }
            if("0".equals(JsonKit.getInfo("no", result).toString())){
            	return (Map<String, Object>) JsonKit.getInfo("data", result);
            }
        } catch (Exception e) {
        	logger.error(e.getMessage(), e);
        }
		return null;
	}
	
	/**
	 * 获取我喜欢的贴吧
	 * @param bduss bduss
	 * @param stoken stoken
	 * @param list tbList
	 * @param fn page
	 */
	public void getMyLikedTB(String bduss, String stoken, List<MyTB> list, String fn){
		try {
			HttpResponse response = hk.execute(Constants.MY_LIKE_URL+"?pn="+fn, createCookie(bduss,stoken));
			String result = EntityUtils.toString(response.getEntity());
			Pattern pattern = Pattern.compile("<tr><td>.+?</tr>");
			Matcher matcher = pattern.matcher(result);
			while(matcher.find()){
				Document doc = Jsoup.parse(matcher.group());
				Elements link  = doc.children();
				for (Element element : link) {
					MyTB tb = new MyTB();
					String ex = element.select(".cur_exp").first().text();//当前经验
					String lv = element.select(".like_badge_lv").first().text();//等级
					String lvName = element.select(".like_badge_title").first().html();//等级名称
					String fid = element.select("span").last().attr("balvid");//贴吧ID（签到关键参数）
					String tbName = element.select("a").first().text();//贴吧名称
					//String url = TIEBA_GET_URL +  URLDecoder.decode(element.select("a").first().attr("href"),"utf-8");//贴吧地址
					String url = Constants.TIEBA_GET_URL + element.select("a").first().attr("href");//贴吧地址
					tb.setEx(Integer.parseInt(ex));
					tb.setFid(Integer.parseInt(fid));
					tb.setTbName(tbName);
					tb.setUrl(url);
					tb.setLv(Integer.parseInt(lv));
					tb.setLvName(lvName);
					list.add(tb);
				}
			}
			Document allDoc = Jsoup.parse(result);
			Elements el = allDoc.getElementsByClass("current");
			for (Element element : el) {
				if(element.nextElementSibling() != null){
					String nextFn = element.nextElementSibling().text();
					getMyLikedTB(bduss, stoken, list, nextFn);
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 获取用户头像URL
	 * @param username 用户id
	 * @return result
	 */
	public String getHeadImg(String username){
		try {
			HttpResponse response = hk.execute(Constants.NEW_HEAD_URL + username + "&ie=utf-8&fr=pb&ie=utf-8");
			String result = EntityUtils.toString(response.getEntity());
			Document doc = Jsoup.parse(result);
			Elements link  = doc.getElementsByAttributeValue("class", "userinfo_head");
			return link.select("img").attr("src");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	/**
	 * 文库签到
	 * @param bduss bduss
	 * @return result
	 */
	public String wenKuSign(String bduss){
		try {
			HashMap<String, Header> headers = new HashMap<String, Header>();
			headers.put("Host", new BasicHeader("Host", "wenku.baidu.com"));
			headers.put("Referer", new BasicHeader("Referer", "https://wenku.baidu.com/task/browse/daily"));
			HttpResponse response = hk.execute(Constants.WENKU_SIGN_URL, createCookie(bduss), headers);
			String result = EntityUtils.toString(response.getEntity());
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 知道签到
	 * @param bduss bduss
	 * @return result
	 */
	public String zhiDaoSign(String bduss){
		try {
			//1.获取stoken
			HttpResponse response = hk.execute(Constants.ZHHIDAO_HOME_URL,createCookie(bduss));
			String stoken = "";
			InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
			char[] buff = new char[1024];
			int length = 0;
			while ((length = reader.read(buff)) != -1) {
				String x = new String(buff, 0, length);
				if(x.contains("stoken")){
					stoken = StrKit.substring(x, "stoken\":\"", "\"");
					break;
				}
			}
			//2.调用签到接口签到
			HashMap<String, Header> headers = new HashMap<String, Header>();
			List<NameValuePair> list = new ArrayList<NameValuePair>();
		    list.add(new BasicNameValuePair("cm", "100509"));
		    list.add(new BasicNameValuePair("stoken", stoken));
		    list.add(new BasicNameValuePair("utdata", "52,52,15,5,9,12,9,52,12,4,15,13,17,12,13,5,13,"+System.currentTimeMillis()));
			HttpResponse res = hk.execute(Constants.ZHHIDAO_API_POST_URL, createCookie(bduss), list, headers);
			String result = EntityUtils.toString(res.getEntity());
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 获取贴吧首页帖子tid列表
	 * @param tbName 贴吧id
	 * @param replyNum 定义标志 根据回复数筛选（回复为0的帖子，抢二楼专用）
	 * @return 帖子tid 数组
	 * 帖子链接：https://tieba.baidu.com/p/  + tid
	 */
	public List<String> getIndexTList(String tbName, Integer replyNum){
		List<String> list = new ArrayList<String>();
		try {
			HttpResponse response = hk.execute(Constants.TIEBA_GET_URL + "/f?kw=" + tbName + "&fr=index");
			String result = EntityUtils.toString(response.getEntity());
			if(StrKit.notBlank(result) && response.getStatusLine().getStatusCode() == 200){
				Document doc_thread = Jsoup.parse(result);
				//解析出帖子code块
				String tcode = doc_thread.getElementById("pagelet_html_frs-list/pagelet/thread_list")
								.html()
								.replace("<!--", "")
								.replace("-->", "");
				//放入新的body解析
				Document doc = Jsoup.parseBodyFragment(tcode);
				Elements link  = doc.getElementsByAttributeValue("class", "j_th_tit "); //帖子链接（获取tid）
				Elements data  = doc.getElementsByAttributeValueMatching("class", "j_thread_list.* clearfix"); //回复数,是否置顶 data-field
				for (int i = 0; i < link.size(); i++) {
					Element element = link.get(i);
					Integer reply= (Integer) JsonKit.getInfo("reply_num",data.get(i).attr("data-field"));
					Object isTop = JsonKit.getInfo("is_top",data.get(i).attr("data-field"));
					if(isTop != null && ("1".equals(isTop.toString()) || "true".equals(isTop.toString()))){//是置顶贴，默认不回复 所以在这里过滤掉
						continue;
					}
					if(replyNum != null){
						if(reply.intValue() == replyNum.intValue()){
							list.add(element.attr("href").substring(3));
						}
					}else{
						list.add(element.attr("href").substring(3));
					}
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return list;
	}
	
	/**
	 * 获取贴吧首页帖子tid列表
	 * @param tbName 贴吧名称
	 * @return 帖子tid 数组
	 * 帖子链接：https://tieba.baidu.com/p/  + tid
	 */
	public List<String> getIndexTList(String tbName){
		return this.getIndexTList(tbName, null);
	}
	
	/**
	 * 获取贴吧fid
	 * @param tbName 贴吧id
	 * @return fid fid
	 */
	@SuppressWarnings("unchecked")
	public String getFid(String tbName){
		String fid = "";
		try {
			HttpResponse response = hk.execute(Constants.TIEBA_FID + tbName);
			String result = EntityUtils.toString(response.getEntity());
			int code = Integer.parseInt(JsonKit.getInfo("no", result).toString());
			if(code == 0) {
				Map<String, Object> data = (Map<String, Object>) JsonKit.getInfo("data", result);
				fid = data.get("fid").toString();
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return fid;
	}
	
	/**
	 * 
	 * @param bduss bduss
	 * @param tid thread_id (getMsg可以获取)
	 * @param pid post_id (getMsg可以获取)
	 * @return floorpid
	 */
	@SuppressWarnings("unchecked")
	public String floorpid(String bduss, String tid, String pid){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", "2"));
			list.add(new BasicNameValuePair("_client_version", "5.0.0"));
			list.add(new BasicNameValuePair("_phone_imei", "642b43b58d21b7a5814e1fd41b08e2a6"));
			list.add(new BasicNameValuePair("kz", tid));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("spid", pid));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.FLOR_PID, createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			String error_code = (String) JsonKit.getInfo("error_code", result);
			if(error_code.equals("0")) {
				Map<String, Object> map = (Map<String, Object>) JsonKit.getInfo("post", result);
				return map.get("id").toString();
			}
		} catch (Exception e) {
		}
		return null;
	}
	
	/**
	 * 回帖-客户端
	 * @param bduss bduss
	 * @param tbs (应该避免频繁获取，因为tbs获取可能会失败){@link #getTbs}
	 * @param tid 帖子id
	 * @param fid 贴吧的fid
	 * @param tbName 贴吧名字
	 * @param content 回帖内容
	 * @param clientType {@link ClientType}，0为随机
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String reply(String bduss, String tbs, String tid, String fid, String tbName, String content, int clientType){
		try {
			if(clientType == 0){//随机选择一种方式
				ClientType[] arr = ClientType.values();
				Random random= new Random();
				int  num = random.nextInt(arr.length);
				clientType = arr[num].getCode();
			}
			HttpRequest request = HttpRequest.post(Constants.REPLY_POST_URL)
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Cookie", "ka=open")
					.header("User-Agent", "bdtb for Android 9.7.8.0")
					.header("Connection", "close")
					.header("Accept-Encoding", "gzip")
					.header("Host", "c.tieba.baidu.com")
					.form("BDUSS", bduss).form("_client_type", clientType)
					.form("_client_version", "9.7.8.0").form("_phone_imei", "000000000000000")
					.form("anonymous", "1").form("content", content)
					.form("fid", fid).form("from", "1008621x")
					.form("is_ad", "0").form("kw", tbName).form("model", "MI+5")
					.form("net_type", "1").form("new_vcode", "1")
					.form("tbs", tbs).form("tid", tid)
					.form("timestamp", System.currentTimeMillis()).form("vcode_tag", "11");
		Map<String, Object> formMap = request.form();
		formMap = MapUtil.sort(formMap);
		StringBuilder sb = new StringBuilder();
		for (String key : formMap.keySet()) {
			sb.append(String.format("%s=%s", key, formMap.get(key)).toString());
		}
		sb.append("tiebaclient!!!");
		String sign = SecureUtil.md5(sb.toString()).toUpperCase();
		String body = request.form("sign", sign).execute().body();
		return body;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 回帖-客户端（tbs、fid调用接口获取）
	 * @param bduss bduss
	 * @param tid 帖子id
	 * @param tbName 贴吧名字
	 * @param content 回帖内容
	 * @param clientType {@link ClientType}，0为随机
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String reply(String bduss, String tid, String tbName, String content, int clientType){
		return reply(bduss, this.getTbs(bduss), tid, this.getFid(tbName), tbName, content, clientType);
	}
	
	/**
	 * 回帖-客户端（随机客户端）
	 * @param bduss bduss
	 * @param tid 帖子id
	 * @param tbName 贴吧名字
	 * @param content 回帖内容
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String reply(String bduss, String tid, String tbName, String content){
		return reply(bduss, this.getTbs(bduss), tid, this.getFid(tbName), tbName, content, 0);
	}
	
	/**
	 * 楼中楼回复-客户端
	 * @param bduss bduss
	 * @param tbs (应该避免频繁获取，因为tbs获取可能会失败){@link #getTbs}
	 * @param tid 帖子id
	 * @param pid 楼层id
	 * @param fid 贴吧fid
	 * @param tbName 贴吧名称
	 * @param content 回帖内容，如果是回复楼中楼（回复+un+:xxx）
	 * @param clientType {@link ClientType}，0为随机
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String replyFloor(String bduss, String tbs, String tid, String pid, String fid, String tbName, String content, int clientType){
		try {
			if(clientType == 0){//随机选择一种方式
				ClientType[] arr = ClientType.values();
				Random random= new Random();
				int  num = random.nextInt(arr.length);
				clientType = arr[num].getCode();
			}
			HttpRequest request = HttpRequest.post(Constants.REPLY_POST_URL)
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Cookie", "ka=open")
					.header("User-Agent", "bdtb for Android 9.7.8.0")
					.header("Connection", "close")
					.header("Accept-Encoding", "gzip")
					.header("Host", "c.tieba.baidu.com")
					.form("BDUSS", bduss).form("_client_type", clientType)
					.form("_client_id", "wappc_1534235498291_488").form("_client_version", "9.7.8.0")
					.form("_phone_imei", "000000000000000").form("anonymous", "1")
					.form("content", content).form("fid", fid)
					.form("kw", tbName).form("model", "MI+5")
					.form("net_type", "1").form("new_vcode", "1")
					.form("post_from", "3").form("quote_id", pid)
					.form("tbs", tbs).form("tid", tid)
					.form("timestamp", System.currentTimeMillis()).form("vcode_tag", "12");
			Map<String, Object> formMap = request.form();
			formMap = MapUtil.sort(formMap);
			StringBuilder sb = new StringBuilder();
			for (String key : formMap.keySet()) {
				sb.append(String.format("%s=%s", key, formMap.get(key)).toString());
			}
			sb.append("tiebaclient!!!");
			String sign = SecureUtil.md5(sb.toString()).toUpperCase();
			String body = request.form("sign", sign).execute().body();
			return body;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		
		return "";
	}
	
	/**
	 * 楼中楼回复-客户端（随机客户端）
	 * @param bduss bduss
	 * @param tid 帖子id
	 * @param pid 楼层id
	 * @param tbName 贴吧名称
	 * @param content 回帖内容，如果是回复楼中楼（回复+un+:xxx）
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String replyFloor(String bduss, String tid, String pid, String tbName, String content){
		return replyFloor(bduss, this.getTbs(bduss), tid, pid, this.getFid(tbName), tbName, content, 0);
	}
	
	/**
	 * 楼中楼回复-客户端（tbs、fid调用接口获取）
	 * @param bduss bduss
	 * @param tid 帖子id
	 * @param pid 楼层id
	 * @param tbName 贴吧名称
	 * @param content 回帖内容，如果是回复楼中楼（回复+un+:xxx）
	 * @param clientType {@link ClientType}，0为随机
	 * @return 回帖接口json，error_code为0代表回帖成功
	 */
	public String replyFloor(String bduss, String tid, String pid, String tbName, String content, int clientType){
		return replyFloor(bduss, this.getTbs(bduss), tid, pid, this.getFid(tbName), tbName, content, clientType);
	}
	
	/**
	 * 查询艾特/回复 信息
	 * @param bduss bduss
	 * @param type reply or at
	 * @param pn pageno
	 * @return result
	 */
	public List<ReplyInfo> getMsg(String bduss, String type, int pn){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", "2"));
			list.add(new BasicNameValuePair("_client_version", "6.2.2"));
			list.add(new BasicNameValuePair("_phone_imei", "864587027315606"));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("pn", pn + ""));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.TBAT_POST_URL + type+ "me", createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			if(logger.isDebugEnabled()) {
	        	logger.debug(result);
	        }
			return JSON.parseArray(JsonKit.getInfo(type + "_list", result).toString(), ReplyInfo.class);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 获取帖子标题
	 * @param tid tid
	 * @return title
	 */
	@SuppressWarnings("unchecked")
	public String getTTitle(String tid){
		String title = "";
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("kz", tid));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.F_PAGE, null, list);
			String result = EntityUtils.toString(response.getEntity());
			title = ((List<Map<String, Object>>) JsonKit.getInfo("post_list", result)).get(0).get("title").toString();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return title;
	}
	/**
	 *  检验帖子id
	 * @param bduss bduss
	 * @param url url
	 * @param fid fid
	 * @return 0 验证通过，-1 帖子所在的贴吧未关注，-2贴吧没有这个帖子 -3帖子不存在
	 */
	public Integer checkT(String bduss, String url, String fid){
		try {
			String isLike = null;
			String fid2 = null;
			HttpResponse response = hk.execute(url,bduss);
			String str = EntityUtils.toString(response.getEntity());
			Pattern pattern = Pattern.compile("islike: '(.*?)'");
			Matcher matcher = pattern.matcher(str);
			if(str.contains("page404")){
				return -3;
			}
			if(matcher.find()){
				isLike = matcher.group(1);
			}
			Pattern fidPattern = Pattern.compile("fid: '(.*?)'");
			Matcher fidMatcher = fidPattern.matcher(str);
			if(fidMatcher.find()){
				fid2 = fidMatcher.group(1);
			}
			if("1".equals(isLike)){
				if(!fid2.equals(fid)){
					return -2;
				}
			}else if("0".equals(isLike)){
				return -1;
			}
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return 0;
	}
	/**
	 * bduss有效性检测(是否是登录状态)
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return true or false
	 */
	public Boolean islogin(String bduss, String stoken){
		try {
			HttpResponse response = hk.execute(Constants.TBS_URL, this.createCookie(bduss, stoken));
			String result = EntityUtils.toString(response.getEntity());
			return (Integer) JsonKit.getInfo("is_login", result)==1?true:false;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	/**
	 * bduss有效性检测(是否是登录状态)
	 * @param bduss bduss
	 * @return true or false
	 */
	public Boolean islogin(String bduss) {
		try {
			HttpResponse response = hk.execute(Constants.BAIDU_URL, createCookie(bduss));
			String html = EntityUtils.toString(response.getEntity());
			Document doc = Jsoup.parse(html);
			if(doc.getElementsByClass("user-name").hasText()) {
				return true;
			}else{
				return false;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return false;
	}
	
	/**
	 * 获取登录二维码图片url
	 * @return 二维码图片url
	 */
	public Map<String, Object> getQRCodeUrl(){
		Map<String, Object> map = new HashMap<String, Object>();
		String gid = UUID.randomUUID().toString();
		Long time = System.currentTimeMillis();
		String getParam = "?lp=pc&gid="+gid+"&callback=tangram_guid_"+time+"&apiver=v3&tt="+System.currentTimeMillis()+"&tpl=mn";
		try {
			
			HttpResponse response = hk.execute(Constants.GET_QRCODE_SIGN+getParam);
			String result = EntityUtils.toString(response.getEntity());
			String sign = JsonKit.getPInfo("sign", result).toString();
			if(sign != null) {
				 map.put("codeUrl", String.format(Constants.GET_QRCODE_IMG, sign).toString());
				 map.put("gid", gid);
				 map.put("sign", sign);
				 map.put("time", time+"");
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return map;
	}
	
	/**
	 * 扫描二维码登录获取cookie
	 * @param v v
	 * @return bduss,stoken
	 */
	public Map<String, Object> getCookieFromQRCode(String v){
		Map<String, Object> map = new HashMap<String, Object>();
		try {
			HttpResponse response = hk.execute("https://passport.baidu.com/v3/login/main/qrbdusslogin"
					+ "?v="+System.currentTimeMillis()
					+ "&bduss="+v
					+ "&u=https%253A%252F%252Ftieba.baidu.com%252Findex.html"
					+ "&loginVersion=v4"
					+ "&qrcode=1"
					+ "&tpl=tb"
					+ "&apiver=v3"
					+ "&tt="+System.currentTimeMillis()
					+ "&traceid="
					+ "&callback=bd__cbs__9txc5");
			String bduss = "";
			String ptoken = "";
			String stoken = "";
			for (Header header : response.getHeaders("Set-Cookie")) {
				String cookieHeader = header.getValue();
				if(cookieHeader.contains("BDUSS=")){
					bduss = StrKit.substring(cookieHeader, "BDUSS=", ";");
					map.put("bduss", bduss);
				}else if(cookieHeader.contains("PTOKEN=")){
					ptoken = StrKit.substring(cookieHeader, "PTOKEN=", ";");
				}
			}
			stoken = hk.doGetStoken(Constants.PASSPORT_AUTH_URL,createCookie(bduss, null, ptoken));
			map.put("stoken", stoken);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return map;
	}
	
	/**
	 * 根据bduss和stoken生成cookie
	 * @param bduss bduss
	 * @param stoken stoken
	 * @param ptoken ptoken
	 * @return cookie
	 */
	public String createCookie(String bduss, String stoken, String ptoken){
		StringBuilder sb = new StringBuilder();
		if(StrKit.isBlank(bduss)){
			return null;
		}else{
			sb.append("BDUSS=");
			sb.append(bduss);
		}
		if(!StrKit.isBlank(stoken)){
			sb.append(";");
			sb.append("STOKEN=");
			sb.append(stoken);
		}
		if(!StrKit.isBlank(ptoken)){
			sb.append(";");
			sb.append("PTOKEN=");
			sb.append(ptoken);
		}
		return sb.toString();
	}
	/**
	 * 根据bduss和stoken生成cookie
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return cookie
	 */
	public String createCookie(String bduss, String stoken){
		return createCookie(bduss, stoken, null);
	}
	/**
	 * 根据bduss生成cookie
	 * @param bduss bduss
	 * @return cookie
	 */
	public String createCookie(String bduss){
		return createCookie(bduss, null, null);
	}
	
	/**
	 * 根据时区 获取当前时间（Asia/Shanghai）
	 * @param _timeZone
	 * @return 当前时间date
	 */
	private static Date realTime(String _timeZone){
	     TimeZone timeZone = null;
	     if(StrKit.isBlank(_timeZone)){
	         timeZone = TimeZone.getDefault();
	     }else{
	         timeZone = TimeZone.getTimeZone(_timeZone);
	     }
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日  HH时mm分ss秒");
		sdf.setTimeZone(timeZone);
		DateFormat df = new SimpleDateFormat("yyyy年MM月dd日  HH时mm分ss秒");
		Date date = null;
		try {
			date = df.parse(df.format(new Date()));
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (java.text.ParseException e) {
			e.printStackTrace();
		}
		return date;
	}
	
	/**
	 * 分页获取关注列表
	 * @param bduss bduss
	 * @param pn 页码
	 * @return 关注列表
	 */
	public String getFollowPage(String bduss, Integer pn){
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("BDUSS", bduss));
		list.add(new BasicNameValuePair("_client_id", "wappc_1542694366490_105"));
		list.add(new BasicNameValuePair("_client_type", "2"));
		list.add(new BasicNameValuePair("_client_version", "9.8.8.13"));
		list.add(new BasicNameValuePair("pn", pn + ""));
		list.add(new BasicNameValuePair("timestamp", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.GET_FOLLOW_LIST, null, list);
			return EntityUtils.toString(response.getEntity());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 获取关注列表
	 * @param bduss bduss
	 * @param pn 页码
	 * @param fList 关注合集
	 */
	@SuppressWarnings("unchecked")
	private void getFollowList(String bduss, Integer pn, List<Map<String,Object>> fList){
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("BDUSS", bduss));
		list.add(new BasicNameValuePair("_client_id", "wappc_1542694366490_105"));
		list.add(new BasicNameValuePair("_client_type", "2"));
		list.add(new BasicNameValuePair("_client_version", "9.8.8.13"));
		list.add(new BasicNameValuePair("pn", pn + ""));
		list.add(new BasicNameValuePair("timestamp", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.GET_FOLLOW_LIST, null, list);
			String result = EntityUtils.toString(response.getEntity());
			fList.addAll((List<Map<String, Object>>) JSONPath.eval(JSON.parse(result), "follow_list"));
			String hasMore = JsonKit.getInfo("has_more", result).toString();
			if(hasMore.equals("1")) {
				//还有下一页
				pn++;
				this.getFollowList(bduss, pn, fList);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 获取全部关注
	 * @param bduss bduss
	 * @return 全部关注
	 */
	public List<Map<String, Object>> getFollowList(String bduss){
		List<Map<String, Object>> fList = new ArrayList<Map<String, Object>>();
		this.getFollowList(bduss, 1, fList);
		return fList;
	}
	/**
	 * 分页获取粉丝
	 * @param bduss bduss
	 * @param pn 页码
	 * @return 粉丝列表
	 */
	public String getFansPage(String bduss, Integer pn){
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("BDUSS", bduss));
		list.add(new BasicNameValuePair("_client_id", "wappc_1542694366490_105"));
		list.add(new BasicNameValuePair("_client_type", "2"));
		list.add(new BasicNameValuePair("_client_version", "9.8.8.13"));
		list.add(new BasicNameValuePair("pn", pn + ""));
		list.add(new BasicNameValuePair("timestamp", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.GET_FANS_LIST, null, list);
			return EntityUtils.toString(response.getEntity());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 获取粉丝列表
	 * @param bduss bduss
	 * @param pn 页码
	 * @param fList 粉丝集合
	 */
	@SuppressWarnings("unchecked")
	private void getFansList(String bduss, Integer pn, List<Map<String,Object>> fList){
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("BDUSS", bduss));
		list.add(new BasicNameValuePair("_client_id", "wappc_1542694366490_105"));
		list.add(new BasicNameValuePair("_client_type", "2"));
		list.add(new BasicNameValuePair("_client_version", "9.8.8.13"));
		list.add(new BasicNameValuePair("pn", pn + ""));
		list.add(new BasicNameValuePair("timestamp", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.GET_FANS_LIST, null, list);
			String result = EntityUtils.toString(response.getEntity());
			fList.addAll((List<Map<String, Object>>) JSONPath.eval(JSON.parse(result), "user_list"));
			String hasMore = (String) JSONPath.eval(JSON.parse(result), "$.page.has_more");
			if(hasMore.equals("1")) {
				//还有下一页
				pn++;
				this.getFansList(bduss, pn, fList);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 获取全部粉丝
	 * @param bduss bduss
	 * @return 全部粉丝
	 */
	public List<Map<String, Object>> getFansList(String bduss){
		List<Map<String, Object>> fList = new ArrayList<Map<String, Object>>();
		this.getFansList(bduss, 1, fList);
		return fList;
	}
	
	/**
	 * 移除粉丝
	 * @param bduss bduss
	 * @param fans_uid 用户id
	 * @return 结果
	 */
	public String removeFans(String bduss, String fans_uid){
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("BDUSS", bduss));
		list.add(new BasicNameValuePair("_client_id", "wappc_1542694366490_105"));
		list.add(new BasicNameValuePair("_client_type", "2"));
		list.add(new BasicNameValuePair("_client_version", "9.8.8.13"));
		list.add(new BasicNameValuePair("fans_uid", fans_uid));
		list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
		list.add(new BasicNameValuePair("timestamp", System.currentTimeMillis()+""));
		list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
		try {
			HttpResponse response = hk.execute(Constants.REMOVE_FANS, null, list);
			return EntityUtils.toString(response.getEntity());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 举报帖子
	 * @param bduss bduss
	 * @param pid 帖子id
	 * @param jubaotype 举报类型
	 * 10001 低俗色情
	 * 10002 垃圾广告
	 * 10002 内容低俗无意义
	 * 10004 辱骂攻击
	 * 10005 其他违法信息
	 * 20001 抄袭我的内容
	 * 20002 暴露我的隐私
	 * 20003 内容里有关我的不实描述
	 * @param reason 举报原因
	 * @return 举报结果{"errno":21,"msg":"您已举报过该贴，我们将于24小时内通过系统消息发送处理结果。"}，{"errno":0,"msg":"举报成功，我们将于24小时内通过系统消息发送处理结果。"}
	 */
	public String jubao(String bduss, String pid, String jubaotype, String reason){
		try {
			HttpResponse response = hk.execute(String.format(Constants.TOOUSU_CHECK, pid), createCookie(bduss));
			String result = EntityUtils.toString(response.getEntity());
			String url = (String)JSONPath.eval(JSON.parse(result), "$.data.url");
			if(StrKit.isBlank(url)) {
				return "{\"errno\":21,\"msg\":\"您已举报过该贴，我们将于24小时内通过系统消息发送处理结果。\"}";
			}
			url = url.replaceAll("&amp;", "&");
			HttpResponse rep2 = hk.execute(url, createCookie(bduss));
			String add = EntityUtils.toString(rep2.getEntity());
			Document doc = Jsoup.parse(add);
			String category  = doc.getElementById("category").val();
			String product_id  = doc.getElementById("product_id").val();
			String client  = doc.getElementById("client").val();
			String submit_token  = doc.getElementById("submit_token").val();
			String sign  = doc.getElementById("sign").val();
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("product_id", product_id));
			list.add(new BasicNameValuePair("client", client));
			list.add(new BasicNameValuePair("category", category));
			list.add(new BasicNameValuePair("submit_token", submit_token));
			list.add(new BasicNameValuePair("sign", sign));
			list.add(new BasicNameValuePair("jubaotype",jubaotype));
			list.add(new BasicNameValuePair("reason",reason));
			list.add(new BasicNameValuePair("pid",pid));
			String submit_r =  EntityUtils.toString(hk.execute(Constants.TOOUSU_SUBMIT,  createCookie(bduss), list).getEntity());
			Integer code = (Integer)JsonKit.getInfo("errno", submit_r);
			if(code == 0) {
				return "{\"errno\":0,\"msg\":\"举报成功，我们将于24小时内通过系统消息发送处理结果。\"}";
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 举报帖子
	 * @param bduss bduss
	 * @param pid 帖子id
	 * @param jubaotype 举报类型
	 * 10001 低俗色情
	 * 10002 垃圾广告
	 * 10002 内容低俗无意义
	 * 10004 辱骂攻击
	 * 10005 其他违法信息
	 * 20001 抄袭我的内容
	 * 20002 暴露我的隐私
	 * 20003 内容里有关我的不实描述
	 * @return 举报结果{"errno":21,"msg":"您已举报过该贴，我们将于24小时内通过系统消息发送处理结果。"}，{"errno":0,"msg":"举报成功，我们将于24小时内通过系统消息发送处理结果。"}
	 */
	public String jubao(String bduss, String pid, String jubaotype){
		return jubao(bduss, pid, jubaotype, "");
	}
	
	/**
	 * 查询是否关注某个贴吧
	 * @param tbName 贴吧名称
	 * @param bduss bduss
	 * @param stoken stoken
	 * @return true or false
	 */
	public Boolean isFocus(String tbName, String bduss, String stoken) {
		try {
			HttpResponse response = hk.execute(String.format(Constants.TIEBA_URL_HTTPS, tbName),  createCookie(bduss, stoken));
			if(response == null) {
				response  = hk.execute(String.format(Constants.TIEBA_URL_HTTP, tbName),  createCookie(bduss, stoken));
			}
			String result = EntityUtils.toString(response.getEntity());
			if(StrKit.substring(result, "'islike': '", "'").equals("1")) {
				return true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return false;
	}
	
	/**
	 * 查询是否关注某个贴吧
	 * @param tbName 贴吧名称
	 * @param bduss bduss
	 * @return true or false
	 */
	public Boolean isFocus(String tbName, String bduss) {
		HttpKit hk = HttpKit.getInstance();
		Boolean flag = false;
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("fid", api.getFid(tbName)));
			list.add(new BasicNameValuePair("kw", tbName));
			list.add(new BasicNameValuePair("tbs", api.getTbs(bduss)));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.LIKE_TIEBA_URL, null, list);
			String fr = EntityUtils.toString(response.getEntity());
			Integer isLike = (Integer)JSONPath.eval(JSON.parse(fr), "$.info.is_like");
			if(isLike == 1) {
				flag = true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		if(flag == false) {
			//查询结果后如果未关注，需要取消关注
			this.unfocus(bduss, tbName);
		}
		return flag;
	}
	
	/**
	 * 获取用户名
	 * @param bduss bduss
	 * @return 用户名
	 */
	public String getUserName(String bduss) {
		try {
			String result = EntityUtils.toString(hk
					.execute(Constants.BAIDU_URL,createCookie(bduss)).getEntity());
			Document doc = Jsoup.parse(result);
			String username = doc.getElementsByClass("user-name").text();
			return username;
		}catch (Exception e) {
		}
		return null;
	}
	
	/**
	 * 一键签到
	 * @param bduss bduss
	 * @return 签到结果
	 */
	public Map<String, Object> oneBtnToSign(String bduss) {
		Long start = System.currentTimeMillis();
		Map<String, Object> msg = new HashMap<String, Object>();
		//1.先获取用户关注的贴吧
		List<Map<String, Object>> list = getLikedTb(bduss);
		String tbs = this.getTbs(bduss);
		int totalCount = list.size();
		//2.一键签到
		List<Map<String, Object>> results = list.stream()
			.parallel()
			.map(p -> {
				return this.signOneTieBa(p.get("name").toString(), Integer.parseInt(p.get("id").toString()), bduss, tbs);
		}).collect(Collectors.toList());
		long signCount = results.stream().filter(r -> r.get("error_code").toString().equals("0")).count();
		long signedCount = results.stream().filter(r -> r.get("error_code").toString().equals("160002")).count();
		msg.put("用户贴吧数", totalCount);
		msg.put("签到成功", signCount);
		msg.put("已签到", signedCount);
		msg.put("签到失败", (totalCount - signedCount - signCount));
		msg.put("耗时", (System.currentTimeMillis()-start)+"ms");
		return msg;
	}
	
	/**
	 * 名人堂助攻
	 * @param bduss bduss
	 * @param tbName 贴吧名称
	 * @return 结果
	 * no=210009 //不在名人堂
	 * no=3110004 //暂未关注
	 * no=2280006 //已助攻
	 * no=0 //成功
	 */
	public String support(String bduss, String tbName) {
		return support(bduss, tbName, getTbs(bduss));
	}
	/**
	 * 名人堂助攻
	 * @param bduss bduss
	 * @param tbName 贴吧名称
	 * @param tbs tbs
	 * @return 结果
	 * no=210009 //不在名人堂
	 * no=3110004 //暂未关注
	 * no=2280006 //已助攻
	 * no=0 //成功
	 */
	public String support(String bduss, String tbName, String tbs) {
		String suportResult = "";
		try {
			HttpResponse hp = hk.execute(String.format(Constants.TIEBA_URL_HTTPS, tbName));
			if(hp == null) {
				hp = hk.execute(String.format(Constants.TIEBA_URL_HTTP, tbName));
			}
			String fidHtml= EntityUtils.toString(hp.getEntity());
			String fid = StrKit.substring(fidHtml, "forum_id\":", ",");
			String cookie = createCookie(bduss);
			List<NameValuePair> params = new ArrayList<>();
			params.add(new BasicNameValuePair("forum_id", fid));
			params.add(new BasicNameValuePair("tbs", tbs));
			//3.获取npc_id
			String result = EntityUtils.toString(hk.execute(Constants.GET_FORUM_SUPPORT, cookie, params).getEntity());
			String npcId = JSONPath.eval(JSON.parse(result), "$.data[0].npc_info.npc_id").toString();
			params.add(new BasicNameValuePair("npc_id", npcId));
			//3.助攻
			suportResult = EntityUtils.toString(hk.execute(Constants.FORUM_SUPPORT,cookie, params).getEntity());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return suportResult;
	}
	
	/**
	 * 获取封禁原因列表
	 * @param bduss bduss
	 * @param tbName 贴吧名称
	 * @param uid 用户uid
	 * @return 封禁原因列表
	 */
	@SuppressWarnings("unchecked")
	public List<String> prisionReasonList(String bduss, String tbName, String uid){
		List<String> reasonList = null;
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "wappc_1451451147094_870"));
			list.add(new BasicNameValuePair("_client_type", "2"));
			list.add(new BasicNameValuePair("_client_version", "6.2.2"));
			list.add(new BasicNameValuePair("_phone_imei", "864587027315606"));
			list.add(new BasicNameValuePair("forum_id", getFid(tbName)));
			list.add(new BasicNameValuePair("user_id", uid));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = hk.execute(Constants.BAWU_POST_URL, createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			String code = (String) JsonKit.getInfo("error_code", result);
			if("0".equals(code)){
				reasonList = (List<String>) JsonKit.getInfo("reason", result);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return reasonList;
	}
	
	/**
	 * 封禁用户
	 * @param bduss bduss
	 * @param userName 要封禁的用户名
	 * @param tbName 贴吧名称
	 * @param days 封禁天数（一般是1,3,10 10是上限）
	 * @param reason 封禁原因 {@link TieBaApi#prisionReasonList}
	 * @return 封禁的结果
	 */
	public String commitprison(String bduss, String userName, String tbName, Integer days, String reason){
		return commitprison(bduss, this.getTbs(bduss), userName, tbName, days, reason, null);
	}
	
	/**
	 * 封禁用户
	 * @param bduss bduss
	 * @param userName 要封禁的用户名
	 * @param tbName 贴吧名称
	 * @param days 封禁天数（一般是1,3,10 10是上限）
	 * @param reason 封禁原因 {@link TieBaApi#prisionReasonList}
	 * @param portrait portrait
	 * @return 封禁的结果
	 */
	public String commitprison(String bduss, String userName, String tbName, Integer days, String reason, String portrait){
		return commitprison(bduss, this.getTbs(bduss), userName, tbName, days, reason, portrait);
	}
	
	/**
	 * 封禁用户
	 * @param bduss bduss
	 * @param tbs tbs
	 * @param userName 要封禁的用户名或是覆盖名（没有用户名的时候）
	 * @param tbName 贴吧名称
	 * @param days 封禁天数（一般是1,3,10 10是上限）
	 * @param reason 封禁原因 {@link TieBaApi#prisionReasonList}
	 * @return 封禁的结果
	 */
	public String commitprison(String bduss, String tbs, String userName, String tbName, Integer days, String reason, String portrait){
		try {
			Map<String, Object> paramMap = new HashMap<String, Object>();
			paramMap.put("BDUSS", bduss);
			paramMap.put("_client_type", 2);
			paramMap.put("_client_version", "11.2.8.1");
			paramMap.put("day", days);
			paramMap.put("fid", getFid(tbName));
			paramMap.put("ntn", "banid");
			if(StrUtil.isNotBlank(portrait)) {
				paramMap.put("portrait", portrait);
				paramMap.put("nick_name", userName);
				paramMap.put("un", "");
			}else {
				paramMap.put("un", userName);
			}
			paramMap.put("reason", reason);
			paramMap.put("tbs", tbs);
			paramMap.put("timestamp", System.currentTimeMillis());
			paramMap.put("word", tbName);
			paramMap.put("z", "1234");
			JSONObject result = HttpKit.commonRequest(Constants.PRISION_POST_URL, paramMap);
			return result.toJSONString();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 根据uname获取uid
	 * @param uname uname
	 * @return uid
	 */
	@SuppressWarnings("unchecked")
	public String getUidByUname(String uname) {
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("search_key", uname));
			list.add(new BasicNameValuePair("_client_version", "6.2.2"));
			list.add(new BasicNameValuePair("sign", StrKit.md5Sign(list)));
			HttpResponse response = HttpKit.getInstance().execute(Constants.SEARCH_FRIEND, null, list);
			String result = EntityUtils.toString(response.getEntity());
			if(!JsonKit.getInfo("errorno", result).toString().equals("0")) {
				logger.info("用户信息查找失败");
			}else {
				return ((List<Map<String, Object>>) JsonKit.getInfo("user_info", result)).get(0).get("user_id").toString();
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}
	
	/**
	 * 根据BDUSS获取关注吧
	 * @param bduss bduss
	 * @return 返回关注贴吧
	 */
	public List<Map<String, Object>> getLikedTb(String bduss) {
		List<Map<String, Object>> tiebas = new ArrayList<Map<String, Object>>();
		this.getTbsByUid(this.getUidByUname(this.getUserName(bduss)), tiebas, 1, null, bduss);
		return tiebas;
	}
	
	/**
	 * 扫码登录状态查询（获取参数v）
	 * @param sign sign {@link TieBaApi#getQRCodeUrl()}
	 * @param gid gid {@link TieBaApi#getQRCodeUrl()}
	 * @return 返回状态结果
	 */
	public JSONObject qrCodeLoginStatus(String sign, String gid) {
		String body = HttpUtil.get(String.format(Constants.QRCODE_LOGIN_STATUS, sign, gid, System.currentTimeMillis()));
		body = StrUtil.subBetween(body, "(", ")");
		JSONObject jo = JSON.parseObject(body);
		return jo;
	}
	
	/**
	 * 获取传图用的 imgtbs
	 * 和 tbs 一样，不用每次传图都获取
	 * @return imgtbs
	 */
	public String getImgTbs() {
		try {
			HttpResponse response = HttpKit.getInstance().execute(Constants.IMGTBS_URL);
			String result = EntityUtils.toString(response.getEntity());
			JSONObject jsonobject = JSON.parseObject(result);
			return jsonobject.getJSONObject("data").getString("tbs");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * 上传图片
	 * @param photo 		上传的图片File
	 * @param bduss			BDUSS
	 * @param imgTbs		{@link #getImgTbs}
	 * @param fid			贴吧id {@link #getFid}
	 * @param saveYunAlbum	是否保存到百度云相册
	 * @param watermarkType	水印类型 {@link WatermarkType}
	 * @return 上传图片结果,img_content用于回帖
	 */
	public JSONObject uploadPicture(File photo, String bduss, String imgTbs, String fid, boolean saveYunAlbum, WatermarkType watermarkType) {
		String url = String.format(Constants.UPLOAD_PHOTOS, imgTbs, fid, saveYunAlbum ? 1 : 0, watermarkType.getCode());
		String result = HttpRequest.post(url).cookie(createCookie(bduss)).form("file", photo).execute().body();
		JSONObject jsonobject = JSON.parseObject(result);
		Integer errorCode = jsonobject.getInteger("error_code");
		if(errorCode == 0) {
			//上传成功
			String picId = jsonobject.getJSONObject("info").getString("pic_id");
			String width = jsonobject.getJSONObject("info").getString("fullpic_width");
			String height = jsonobject.getJSONObject("info").getString("fullpic_height");
			jsonobject.put("img_content", String.format("#(pic,%s,%s,%s)", picId, width, height));
		}
		return jsonobject;
	}
	
	/**
	 * 发帖-客户端
	 * @param bduss bduss
	 * @param tbs tbs(不需要每次都获取)
	 * @param fid 贴吧id {@link #getFid}
	 * @param tbName 贴吧名字
	 * @param title 标题，如果没有标题请设置""
	 * @param content 发帖内容
	 * @param clientType {@link ClientType}，0为随机
	 * @return 发帖结果，判断error_code是不是0
	 */
	public JSONObject addThread(String bduss, String tbs, String fid, String tbName, String title, String content, int clientType) {
		if(clientType == 0){//随机选择一种方式
			ClientType[] arr = ClientType.values();
			Random random= new Random();
			int  num = random.nextInt(arr.length);
			clientType = arr[num].getCode();
		}
		HttpRequest request = HttpRequest.post(Constants.THREAD_ADD_URL)
										.header("Content-Type", "application/x-www-form-urlencoded")
										.header("User-Agent", "bdtb for Android 9.7.8.0")
										.form("BDUSS", bduss).form("_client_type", clientType)
										.form("_client_version", "9.7.8.0").form("_phone_imei", "000000000000000")
										.form("anonymous", "1").form("call_from", "2")
										.form("can_no_forum", "0").form("content", content)
										.form("entrance_type", "1").form("fid", "10733075")
										.form("from", "1001128p").form("is_feedback", "0")
										.form("is_hide", "1").form("is_link_thread","0")
										.form("st_type","notitle").form("is_ntitle", StrUtil.isBlank(title)?1:0)
										.form("kw", tbName).form("net_type", "1")
										.form("new_vcode", "1").form("reply_uid","null")
										.form("takephoto_num", "0").form("tbs", tbs)
										.form("timestamp", System.currentTimeMillis())
										.form("stErrorNums", "0").form("title", title).form("vcode_tag", "12");
		Map<String, Object> formMap = request.form();
		formMap = MapUtil.sort(formMap);
		StringBuilder sb = new StringBuilder();
		for (String key : formMap.keySet()) {
			sb.append(String.format("%s=%s", key, formMap.get(key)).toString());
		}
		sb.append("tiebaclient!!!");
		String sign = SecureUtil.md5(sb.toString()).toUpperCase();
		String result = request.form("sign", sign).execute().body();
		return JSON.parseObject(result);
	}
	
	/**
	 * 发帖-客户端（随机）
	 * @param bduss bduss
	 * @param tbs tbs(不需要每次都获取)
	 * @param fid 贴吧id {@link #getFid}
	 * @param tbName 贴吧名字
	 * @param title 标题，如果没有标题请设置""
	 * @param content 发帖内容
	 * @return 发帖结果，判断error_code是不是0
	 */
	public JSONObject addThread(String bduss, String tbs, String fid, String tbName, String title, String content) {
		return addThread(bduss, tbs, fid, tbName, title, content, 0);
	}
	
	/**
	 * 发帖-客户端
	 * @param bduss bduss
	 * @param tbName 贴吧名字
	 * @param title 标题，如果没有标题请设置""
	 * @param content 发帖内容
	 * @return 发帖结果，判断error_code是不是0
	 */
	public JSONObject addThread(String bduss, String tbName, String title, String content) {
		return addThread(bduss, this.getTbs(bduss), this.getFid(tbName), tbName, title, content, 0);
	}
	
}
