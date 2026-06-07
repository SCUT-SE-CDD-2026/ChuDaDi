@file:Suppress("LongParameterList")

package com.example.chudadi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chudadi.R
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle
import com.example.chudadi.ui.theme.LocalChuUiPalette

private val BgOuter: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.outer

private val BgCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.card

private val BgCardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.cardBorder

private val MainCardShadow: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.cardShadow

private val LeftPanelBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.subtleGlow

private val PlayerInfoBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.row

private val PlayerInfoBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.rowBorder

private val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.textPrimary

private val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.textSecondary

private val DividerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.divider

private val AvatarBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.avatarBg

private val AvatarBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.avatarBorder

private data class RuleSection(
    val title: String,
    val lines: List<String>,
)

private val RuleSections = listOf(
    RuleSection(
        title = "一、游戏基本信息",
        lines = listOf(
            "人数：4人。",
            "牌数：52张，不含大小王，每人13张。",
            "目标：最先出完所有手牌。",
        ),
    ),
    RuleSection(
        title = "二、牌大小规则",
        lines = listOf(
            "点数从小到大：3 < 4 < 5 < 6 < 7 < 8 < 9 < 10 < J < Q < K < A < 2。",
            "2 为最大点数。",
            "同点数比较花色：方块 < 梅花 < 红桃 < 黑桃。",
        ),
    ),
    RuleSection(
        title = "三、基础牌型",
        lines = listOf(
            "单张：1张任意手牌。",
            "对子：2张点数相同的牌。",
            "三张：3张点数相同的牌。",
            "顺子：5张连续点数的牌，不能包含2。",
            "三带二（葫芦）：3张同点数牌 + 1个对子。",
            "同花：5张花色相同的牌。",
            "同花顺：5张花色相同且点数连续的牌，不含2。",
            "铁支（4+1）：4张点数相同的牌 + 1张任意单张。",
        ),
    ),
    RuleSection(
        title = "四、牌型比较",
        lines = listOf(
            "单张、对子、三张按点数判定；点数相同再按花色判定。",
            "五张牌型强弱：顺子 < 同花 < 三带二（葫芦） < 铁支 < 同花顺。",
            "三带二按三张部分的点数比较。",
            "铁支按四张部分的点数比较。",
            "同花、顺子、同花顺按最大点数比较；最大点数相同再按花色比较。",
        ),
    ),
    RuleSection(
        title = "五、南北规则差异",
        lines = listOf(
            "南方玩法：五张牌型一般不能跨牌型互压。",
            "南方玩法例外：同花顺可压任意牌型；铁支可压除同花顺外的任意牌型。",
            "北方玩法：五张牌型允许互压，按同花顺 > 铁支 > 葫芦 > 同花 > 顺子判定。",
        ),
    ),
    RuleSection(
        title = "六、出牌流程",
        lines = listOf(
            "开局由持有方块3的玩家先出，第一手牌必须包含方块3，牌型不限。",
            "按顺时针出牌，每轮玩家可选择出牌或 Pass。",
            "跟牌必须大于上一手牌。",
            "北方玩法：五张牌型可跨牌型跟牌；单张、对子、三张必须同类型跟牌。",
            "南方玩法：必须同牌型跟牌；同花顺、铁支按炸弹例外规则处理。",
        ),
    ),
    RuleSection(
        title = "七、Pass 与顺子规则",
        lines = listOf(
            "Pass 后本轮不可再出。",
            "若所有玩家均 Pass，则由最后出牌者重新发起出牌。",
            "顺子必须为5张连续牌，不能包含2。",
            "A 只能作为高位，不能当成1；不允许 QKA23 等循环顺子。",
            "顺子按最大点数牌判定大小；最大点数相同时按花色判定。",
        ),
    ),
    RuleSection(
        title = "八、顶大特殊规则（包赔）",
        lines = listOf(
            "若下家只剩1张牌（报单），当前玩家出单张时必须出手头最大的单张。",
            "若未出最大单张，导致下家接牌后出完手牌获胜，则当前玩家承担本轮所有扣分。",
            "若当前最大单张也压不住前面的牌，或下家的唯一手牌压不住当前出的单张，则不触发包赔。",
        ),
    ),
    RuleSection(
        title = "九、计分规则",
        lines = listOf(
            "胜者得分为其他所有玩家扣分之和；其他玩家按剩余手牌扣分。",
            "若获胜玩家最终打出的牌型为黑桃2，或牌型中包含黑桃2，本轮计分和扣分翻倍。",
            "南方计分：按剩余牌数扣分；剩余手牌中有2时，该玩家扣分翻倍。",
            "北方计分：剩余少于10张不翻倍；10到12张扣分翻倍；13张未出牌扣52分。",
        ),
    ),
    RuleSection(
        title = "提示",
        lines = listOf("不同地区可能存在地方规则，建议开局前确认玩法设置。"),
    ),
)

@Composable
fun HomeScreen(
    playerName: String = "默认玩家",
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
    onCreateRoom: () -> Unit = {},
    onJoinRoom: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRules: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showRulesDialog by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgOuter)
            .testTag(ComposeTestTags.HOME_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .shadow(elevation = MainCardShadow, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(width = 1.dp, color = BgCardBorder, shape = RoundedCornerShape(24.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LeftPanel(
                    playerName = playerName,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.85f)
                        .align(Alignment.CenterVertically)
                        .background(DividerColor),
                )
                RightPanel(
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onSettings = onSettings,
                    onRules = {
                        showRulesDialog = true
                        onRules()
                    },
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
            }
        }

        noticeMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissNotice,
                title = {
                    Text(
                        text = "提示",
                        color = TextPrimary,
                    )
                },
                text = {
                    Text(
                        text = message,
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissNotice) {
                        Text(text = "知道了", color = TextPrimary)
                    }
                },
                containerColor = BgCard,
            )
        }

        if (showRulesDialog) {
            RuleExplanationDialog(onDismiss = { showRulesDialog = false })
        }
    }
}

@Composable
private fun RuleExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "游戏规则说明",
                color = TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag(ComposeTestTags.RULES_DIALOG_CONTENT),
            ) {
                RuleSections.forEach { section ->
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    section.lines.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭", color = TextPrimary)
            }
        },
        containerColor = BgCard,
        modifier = Modifier.testTag(ComposeTestTags.RULES_DIALOG),
    )
}

@Composable
private fun LeftPanel(
    playerName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(LeftPanelBg, Color.Transparent),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.main_logo),
                contentDescription = "锄大地",
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth(0.85f),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "锄大地",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontSize = 28.sp,
            )
            Text(
                text = "The Big Two",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
        }
        PlayerInfoBlock(playerName = playerName)
    }
}

@Composable
private fun PlayerInfoBlock(playerName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerInfoBg)
            .border(1.dp, PlayerInfoBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AvatarBg)
                .border(1.5.dp, AvatarBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.avatar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = playerName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Text(
                text = "南方规则 · v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun RightPanel(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onSettings: () -> Unit,
    onRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "选择模式",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))

        ChuButton(
            text = "创建房间",
            onClick = onCreateRoom,
            style = ChuButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        ChuButton(
            text = "加入房间",
            onClick = onJoinRoom,
            style = ChuButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChuButton(
                text = "设置",
                onClick = onSettings,
                style = ChuButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f),
            )
            ChuButton(
                text = "规则说明",
                onClick = onRules,
                style = ChuButtonStyle.PRIMARY,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ComposeTestTags.RULES_BUTTON),
            )
        }
    }
}
