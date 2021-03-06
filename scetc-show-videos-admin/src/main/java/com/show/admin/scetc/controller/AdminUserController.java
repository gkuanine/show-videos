package com.show.admin.scetc.controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import com.show.admin.scetc.pojo.AdminUser;
import com.show.admin.scetc.service.AdminUserService;
import com.show.admin.scetc.utils.CommonUtils;
import com.show.admin.scetc.utils.ImageCodeUtils;
import com.show.admin.scetc.utils.XyfJsonResult;

@RestController
@RequestMapping("/adminUser")
public class AdminUserController extends BasicController {

	// 返回首页
	@Autowired
	private AdminUserService adminUserService;

	// 注销操作
	@PostMapping("/logout")
	public XyfJsonResult logout(HttpServletRequest request, HttpServletResponse response) {
		AdminUser adminUser = (AdminUser) request.getSession().getAttribute("adminUser");
		if (adminUser != null) // 如果存在 就从redis缓存数据库中删掉它
		{
			redis.del(User_REDIS_SESSION + adminUser.getId());
			request.getSession().invalidate();// 销毁session 中的数据
		}
		return XyfJsonResult.ok();
	}

	@RequestMapping("/forgetPassword")
	public String forgetPassword(HttpServletRequest request, HttpServletResponse response) {

		return "thymeleaf/forgetPassword";
	}

	// 修改密码请求提交
	@RequestMapping(value = "/changePasswordSubmit", method = RequestMethod.POST)
	public XyfJsonResult changePasswordSubmit(HttpServletRequest request, HttpServletResponse response,
			String oldPassword, String newPassword, String reNewPassword) {

		AdminUser adminUser = (AdminUser) request.getSession().getAttribute("adminUser");
		if (adminUser == null) {
			return XyfJsonResult.errorMsg("修改账号密码失败,请重新登陆");
		}
		if (!reNewPassword.equals(newPassword)) {
			return XyfJsonResult.errorMap("两次输入密码不一致");
		}
		adminUser = adminUserService.selectOneById(adminUser.getId());
		if (!adminUserService.check(oldPassword, adminUser)) {
			return XyfJsonResult.errorMsg("修改密码失败,初始密码错误");
		}
		adminUser.setPassword(CommonUtils.calculateMD5(adminUser.getSalt() + newPassword));
		adminUserService.update(adminUser);
		AdminUser adminUserVo = CommonUtils.formate(adminUser);
		new Thread(new Runnable() {
			@Override
			public void run() {
				SimpleDateFormat formate = new SimpleDateFormat();
				String date = formate.format(new Date());
				redis.lpush(Operate_REDIS_SESSION,
						date + "&nbsp;&nbsp;&nbsp;&nbsp;" + adminUserVo.getRealName() + ":修改了密码");// 存放到redis
			}
		}).start();
		return XyfJsonResult.ok();
	}

	@RequestMapping("/login")
	public ModelAndView login(HttpServletRequest request) {

		return new ModelAndView("thymeleaf/login");
	}

	/**
	 * 校验用户名密码验证码
	 * 
	 * @param username
	 * @param password
	 * @param verifyCode
	 * @return
	 */
	@PostMapping("/loginSubmit")
	public XyfJsonResult loginSubmit(HttpServletRequest request, String username, String password, String verifyCode,
			Model model) {
		if (!ImageCodeUtils.checkImageCode(request.getSession(), verifyCode)) {
			return XyfJsonResult.errorMsg("验证码错误");

		}
		// 尝试登陆操作
		AdminUser adminUser = adminUserService.login(username, password);
		if (adminUser == null) {
			// 登陆失败
			return XyfJsonResult.errorMsg("账号密码错误");
		}
		// 检查账号是否被禁用了
		// 登陆成功,登陆成功之后更新用户的登陆时间
		AdminUser adminUserVo = CommonUtils.formate(adminUser);
		request.getSession().setAttribute("adminUser", adminUserVo);// 将账号密码添加到session 中
		new Thread(new Runnable() {
			@Override
			public void run() {
				SimpleDateFormat formate = new SimpleDateFormat();
				String date = formate.format(new Date());
				redis.set(User_REDIS_SESSION + adminUserVo.getId(), adminUserVo.toString());// 保存账号信息到redis 缓存中
				redis.lpush(Operate_REDIS_SESSION,
						date + "&nbsp;&nbsp;&nbsp;&nbsp;" + adminUserVo.getRealName() + "登陆了系统");
			}
		}).start();
		return XyfJsonResult.ok();
	}

}
