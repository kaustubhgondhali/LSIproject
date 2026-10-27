package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ActiveRequest;
import com.lordsai.lsi.dto.course.CourseDtos.ReorderRequest;
import com.lordsai.lsi.dto.slider.SliderDtos.AdminSlide;
import com.lordsai.lsi.dto.slider.SliderDtos.SliderImageRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.HomepageSliderService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Mutual Fund homepage slider management (ADMIN only — enforced by SecurityConfig for
 * /api/admin/**). The Share Market website has no equivalent — this feature is intentionally
 * scoped to the Mutual Fund homepage's existing hero carousel only.
 */
@RestController
@RequestMapping("/api/admin/mutual-fund/slider")
public class AdminHomepageSliderController {

    private final HomepageSliderService sliderService;
    private final UserService userService;

    public AdminHomepageSliderController(HomepageSliderService sliderService, UserService userService) {
        this.sliderService = sliderService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<AdminSlide>> list() {
        return ApiResponse.ok(sliderService.listAll());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<AdminSlide> create(@Valid @ModelAttribute SliderImageRequest body,
                                          @RequestParam("file") MultipartFile file,
                                          HttpServletRequest req) {
        return ApiResponse.ok("Slide added to the Mutual Fund homepage slider.",
                sliderService.create(body, file, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminSlide> update(@PathVariable Long id, @Valid @ModelAttribute SliderImageRequest body,
                                          HttpServletRequest req) {
        return ApiResponse.ok("Slide updated.", sliderService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public ApiResponse<AdminSlide> replaceImage(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                HttpServletRequest req) {
        return ApiResponse.ok("Image replaced.", sliderService.replaceImage(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/{id}/active")
    public ApiResponse<AdminSlide> setActive(@PathVariable Long id, @RequestBody ActiveRequest body, HttpServletRequest req) {
        return ApiResponse.ok(body.active() ? "Slide activated." : "Slide deactivated.",
                sliderService.setActive(id, body.active(), actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest body, HttpServletRequest req) {
        sliderService.reorder(body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Slider order saved.");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        sliderService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Slide deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
