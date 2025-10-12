// import Vue from "vue";
// import VueRouter from "vue-router";
import { createRouter, RouteRecordRaw, createWebHistory } from 'vue-router';

// 每次调用Vue.$router.push方法跳转路由的时候先判断是不是已经在目标路由，避免重复跳转（Vue会有警告）
// const originalPush = VueRouter.prototype.push;
// VueRouter.prototype.push = function push(location) {
//     return originalPush.call(this, location).catch(err => err);
// };

// Vue.use(VueRouter);

const routes = [
    
    {
        path: "/",
        name: "xx",
        component: () => import("@/components/index/OPT.vue"),
    },{
        path: "/in",
        name: "homp",
        component: () => import("@/components/index/OPT.vue"),
    },
    {
        path: "/rlist",
        name: "home",
        component: () => import("@/components/user/RoomList"),
    },
    
    {
        path: "/hii",
        name: "hi",
        component: () => import("@/components/index/Hi"),
    },
    {
        path: "/wave",
        name: "wave",
        component: () => import("@/components/index/WaveBg.vue"),
    },
    {
	    path: "/editpaper",
	    name: "editpaper",
	    component: () => import("@/components/index/CreatePaper"),
	},
    {
	    path: "/editpaper/:id",
	    name: "editpaperWithId",
	    component: () => import("@/components/index/CreatePaper"),
	},
    {
	    path: "/createscolar",
	    name: "createscolar",
	    component: () => import("@/components/index/Createscolar"),
	},
	{
	    path: "/createscolar/:id",
	    name: "editscolar",
	    component: () => import("@/components/index/Createscolar"),
	},
	{
	    path: "/new",
	    name: "mypage",
	    component: () => import("@/components/index/New"),
	},
    {
        path: "/search",
        component: () => import("@/components/index/OPT.vue"),
    },
    {
        path: "/recommended",
        name: "recommended",
        component: () => import("@/components/index/AuthorsListIndex"),
    },
    {
        path: "/write",
        component: () => import("@/components/index/WriteArticleIndex"),
    },
    {
        path: "/edit/:id",
        component: () => import("@/components/index/WriteArticleIndex"),
    },
    {
        path: "/detail/:id",
        name: "detail",
        component: () => import("@/components/index/ArticleDetailIndex"),
    },
    {
        path: "/rooms",
        name: "rooms",
        component: () => import("@/components/room/RoomManagement.vue"),
    },
    {
        path: "/tasks",
        name: "tasks",
        component: () => import("@/components/index/TaskManagement.vue"),
    },
    {
        path: "/popular",
        name: "PopularRankings",
        component: () => import("@/components/index/PopularRankings.vue")
    },
    {
        path: "/empty",
        component: () => import("@/components/utils/CustomEmpty"),
    },
    {
        path: "/usercenter/:id",
        name:"centernext",
        component: () => import("@/components/index/usercenter"),
        children: [
            {
                path: ":userCenterTab",
                component: () => import("@/components/index/usercenter")
            },
        ]
    },
    {
        path: "/activity/:id",
        name: "activityDetail",
        component: () => import("@/components/index/ActivityDetail.vue")
    },
    {
        path: "/chatroomso/:id",
        name:"socket",
        component: () => import("@/components/index/chatroom"),
        
    },
    {
        path: "/label",
        name: "label",
        component: () => import("@/components/index/LabelIndex"),
    },
    {
        path: "/label/:id",
        component: () => import("@/components/index/LabelToArticleIndex"),
    },
    {
        path: "/settings",
        component: () => import("@/components/index/SetUpIndex"),
        children: [
            {
                path: "",
                redirect: "profile"
            },
            {
                path: "profile",
                name: "profile",
                component: () => import("@/components/user/ProfileContent")
            },
            {
                path: "account",
                name: "account",
                component: () => import("@/components/user/AccountSettings")
            },
        ]
    },
    {
        path: "/resource",
        name: "resource",
        component: () => import("@/components/index/ResourceIndex"),
    },
    {
        path: "/book",
        name: "book",
        component: () => import("@/components/index/Book"),
    },
    {
        path: "/about",
        name: "about",
        component: () => import("@/components/index/About"),
    },
    {
        path: "/commentDonate",
        name: "commentDonate",
        component: () => import("@/components/index/CommentDonateIndex"),
    },
    {
        path: "/500",
        name: '500',
        component: () => import("@/components/errorPage/ServerError")
    },
    {
        path: "/author/:authorId",
        name: "authorIntroduction",
        component: () => import("@/components/index/AuthorIntroduction.vue")
    },
    // 新增用户搜索路由
    {
        path: '/user/search',
        name: 'UserSearch',
        component: () => import("@/components/user/Searchuser.vue")
    },
    {
        path: '/searchuser',
        name: 'SearchUser',
        component: () => import("@/components/user/Searchuser.vue")
    },
    {
        path: '/user/search-demo',
        name: 'UserSearchDemo',
        component: () => import("@/components/user/SearchDemo.vue")
    },
    // 新增 PaperDetail 路由
    {
      path: '/paper/:paperId',
      name: 'PaperDetail',
      component: () => import("@/components/index/PaperDetail.vue")
    },
    {
      path: '/chat/:id',
      name: 'chatroom',
      component: () => import("@/components/index/chat.vue")
    },
    {
      path: '/upload',
      name: 'Upload',
      component: () => import("@/components/index/Upload.vue")
    },
    // Removed Follow.vue import as the file does not exist
    // {
    //   path: '/follow',
    //   name: 'Follow',
    //   component: () => import("@/components/index/Follow.vue")
    // },
    // 新增用户主页(可编辑)路由
    {
      path: '/user/mainpage/:userId',
      name: 'UserMainPage',
      component: () => import("@/components/user/UserMainPage.vue")
    },
    // 新增用户动态路由
    {
      path: '/user/activities/:userId',
      name: 'UserActivities',
      component: () => import("@/components/user/UserActivities.vue")
    },
    // 权限管理路由
    {
      path: '/permission',
      name: 'PermissionManagement',
      component: () => import("@/components/index/PermissionManagement.vue")
    },
    // OAuth 回调路由
    {
      path: '/oauth/callback',
      name: 'OAuthCallback',
      component: () => import("@/components/login/OAuthCallback.vue")
    },
    // 学者认领管理路由
    {
      path: '/scholar-claim',
      name: 'ScholarClaimManagement',
      component: () => import("@/components/scholar/ScholarClaimManagement.vue")
    },
    // 学者认领申请页面
    {
      path: '/scholar-claim/apply/:scholarId',
      name: 'ScholarClaimApply',
      component: () => import("@/components/scholar/ScholarClaimApply.vue")
    },
    // 后台管理
    {
      path: '/admin',
      name: 'AdminDashboard',
      component: () => import("@/components/admin/AdminDashboard.vue")
    },
    // 机构管理
    {
      path: '/institution',
      name: 'InstitutionManagement',
      component: () => import("@/components/institution/InstitutionManagement.vue")
    },
    // 消息通知页面
    {
      path: '/notifications',
      name: 'NotificationPage',
      component: () => import("@/views/NotificationPage.vue")
    },
    // RAG资料售卖平台路由
    {
      path: '/materials',
      name: 'MaterialList',
      component: () => import("@/components/material/MaterialList.vue")
    },
    {
      path: '/materials/chat',
      name: 'MaterialChatCenter',
      component: () => import("@/components/material/MaterialChat.vue")
    },
    {
      path: '/material/upload',
      name: 'MaterialUpload',
      component: () => import("@/components/material/MaterialUpload.vue")
    },
    {
      path: '/material/:id/edit',
      name: 'MaterialEdit',
      component: () => import("@/components/material/MaterialUpload.vue")
    },
    {
      path: '/material/:id/chat',
      name: 'MaterialChat',
      component: () => import("@/components/material/MaterialChat.vue")
    },
    {
      path: '/material/:id',
      name: 'MaterialDetail',
      component: () => import("@/components/material/MaterialDetail.vue")
    },
    {
      path: '/orders',
      name: 'OrderList',
      component: () => import("@/components/material/OrderList.vue")
    },
    {
      path: '/screen',
      name: 'ScreenMonitor',
      component: () => import("@/components/index/ScreenMonitor.vue")
    },
    // 导师推荐系统路由
    {
      path: '/mentor-system',
      component: () => import("@/components/mentor/MentorLayout.vue"),
      redirect: '/mentor-system/mentors',
      children: [
        {
          path: 'mentors',
          name: 'MentorList',
          component: () => import("@/components/mentor/MentorList.vue")
        },
        {
          path: 'mentors/:id',
          name: 'MentorDetail',
          component: () => import("@/components/mentor/MentorDetail.vue")
        },
        {
          path: 'students',
          name: 'StudentList',
          component: () => import("@/components/mentor/StudentList.vue")
        },
        {
          path: 'students/:id',
          name: 'StudentDetail',
          component: () => import("@/components/mentor/StudentDetail.vue")
        },
        {
          path: 'recommendations',
          name: 'MentorRecommendations',
          component: () => import("@/components/mentor/RecommendedMentors.vue")
        },
        {
          path: 'applications',
          name: 'MentorApplications',
          component: () => import("@/components/mentor/ApplicationList.vue")
        },
        {
          path: 'chat/:applicationId',
          name: 'MentorChatRoom',
          component: () => import("@/components/mentor/ChatRoom.vue")
        }
      ]
    },
    {
        path: '/daily-report',
        name: 'DailyReport',
        component: () => import('@/components/index/DailyReport.vue')
    },
    // 抽奖页面
    {
        path: '/lottery',
        name: 'Lottery',
        component: () => import('@/components/lottery/Lottery.vue')
    },
    {
        path: "/:catchAll(.*)",
        name: "404",
        component: () => import("@/components/errorPage/NotFound.vue")
    }
];

const router = createRouter({
    // history: createWebHashHistory(),
    history: createWebHistory(),
    routes,
});

export default router;
